package com.bizcon.taxesautomator.services;

import com.bizcon.taxesautomator.controllers.MainController;
import com.bizcon.taxesautomator.models.CertificateModel;
import com.bizcon.taxesautomator.models.DocumentModel;
import com.bizcon.taxesautomator.models.MessageDto;
import com.bizcon.taxesautomator.utils.ApiUtils;
import com.bizcon.taxesautomator.utils.UiModifier;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.prefs.Preferences;

public class ApiService implements ApiUtils {
    private final HttpClient client;
    private final String BASE_URL = "https://new.e-taxes.gov.az/api/po";
    private final Preferences prefs = Preferences.userNodeForPackage(MainController.class);
    private int PROCESSED_RECORDS = 0;
    private int TOTAL_RECORDS_COUNT;
    private boolean isCompletionNotified = false;

    private int FAILED_RECORDS_COUNT = 0;
    private int COMPLETED_RECORDS_COUNT = 0;

    protected UiModifier uiModifier;

    protected ApiService() {
        System.out.println("ApiService created!");
        client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();
    }

    private void checkProcessingCompletion(){
        if (PROCESSED_RECORDS >= TOTAL_RECORDS_COUNT && !isCompletionNotified){
            System.out.println("Processing completed!");
            System.out.println(PROCESSED_RECORDS);
            System.out.println(TOTAL_RECORDS_COUNT);
            uiModifier.notifyCompletion(
                    "Emeliyyat ugurla bitdi!\n Ugurlu: %s | Ugursuz: %s"
                            .formatted(COMPLETED_RECORDS_COUNT, FAILED_RECORDS_COUNT
                            ));
            isCompletionNotified = true;
        }
    }

    protected void getRequestStatus(
            String token, String asanID, String searchElement,
            String dateStart, String dateEnd, boolean unread, boolean makeReport
    ) throws URISyntaxException {
        Timer timer = new Timer();
        final int MAX_RETRIES = 18;
        final int[] retryCount = { 0 };
        final boolean[] firstRequestTriggered = {false};
        AtomicReference<String> cookieValue = new AtomicReference<>();

        HttpRequest browserHistoryRequest = HttpRequest.newBuilder()
            .uri(new URI("https://new.e-taxes.gov.az/api/po/log/public/v1/activity"))
            .header("Authorization", "Bearer " + token)
            .header("Content-Type", "application/json")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:142.0) Gecko/20100101 Firefox/142.0")
            .POST(HttpRequest.BodyPublishers.ofString(
            "{ \"actionType\": \"BROWSER_HISTORY_CHANGE\", " +
                    "\"description\": \"User changed location to: {\\\"pathname\\\":\\\"/verification/asan\\\"," +
                    " \\\"search\\\":\\\"\\\", \\\"hash\\\":\\\"\\\", \\\"key\\\":\\\"" + generateRandomKey() + "\\\"}\" }"
            ))
            .build();

            client.sendAsync(browserHistoryRequest, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response ->
                        System.out.println("Browser history POST status: " + response.statusCode()));

        HttpRequest firstRequest = formRequest(token, null);

        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                System.out.println("Current retry count: " + retryCount[0]);

                if (retryCount[0] >= MAX_RETRIES) {
                    timer.cancel();
                    System.out.println("Max retries reached, stopping timer");
                    uiModifier.markAsFailed(asanID);
                    PROCESSED_RECORDS ++;
                    FAILED_RECORDS_COUNT ++;
                    return;
                }

                if (!firstRequestTriggered[0]) {
                    System.out.println("First request triggered");
                    GetAPIStatusState(firstRequest, timer, cookieValue, token, asanID, searchElement,
                            dateStart, dateEnd, unread, makeReport);
                    firstRequestTriggered[0] = true;
                } else {
                    try {
                        HttpRequest newRequest = formRequest(token, cookieValue.get());
                        GetAPIStatusState(newRequest, timer, cookieValue, token, asanID, searchElement,
                                dateStart, dateEnd, unread, makeReport);
                    } catch (URISyntaxException e) {
                        timer.cancel();
                        throw new RuntimeException(e);
                    }
                }

                retryCount[0]++;
            }
        }, 0, 5000);
    }




    private void GetAPIStatusState(
            HttpRequest request, Timer timer, AtomicReference<String> cookieValue, String token,
            String asanID, String searchElement, String dateStart, String dateEnd, boolean unread, boolean makeReport
    ) {
        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenAccept(response -> {
                System.out.println("API STATUS STA2TE: " + response.statusCode());
                System.out.println("Intermediary API body");
                System.out.println(response.body());

                System.out.println("API STATUS STATE RESPONSE HEADERS");
                System.out.println(response.headers());

                if (response.statusCode() == 200 && response.body().contains("\"successful\":true")) {
                    timer.cancel();
                    try {
                        System.out.println(response.body());
                        System.out.println("REQUEST SUCCESSFUL! OBTAINING CERTIFICATES ...");
                        getCertificates(cookieValue.get(), token, asanID, searchElement,
                                dateStart, dateEnd, unread, makeReport);
                    } catch (URISyntaxException e) {
                        uiModifier.markAsFailed(asanID);
                        PROCESSED_RECORDS ++;
                        FAILED_RECORDS_COUNT ++;
                        checkProcessingCompletion();
                        throw new RuntimeException(e);
                    }
                }

                cookieValue.set(response.headers()
                    .firstValue("Set-Cookie")
                    .map(value -> value.split(";")[0])
                    .orElse(null));
            });
    }

    //1
    protected void authenticateClient(
            String phone, String asanID,
            String searchElement, String dateStart, String dateEnd, boolean unread, boolean makeReport
    ) throws URISyntaxException {
        URI endpoint = new URI(BASE_URL + "/auth/public/v1/asanImza/start");
        HttpRequest request = HttpRequest.newBuilder()
            .POST(HttpRequest.BodyPublishers.ofString("{\"phone\":\"" + phone + "\", \"userId\":\"" + asanID + "\"}"))
            .uri(endpoint)
            .header("Content-Type", "application/json")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:142.0) Gecko/20100101 Firefox/142.0")
            .build();

        CompletableFuture<HttpResponse<String>> responseFuture = client.sendAsync(request, HttpResponse.BodyHandlers.ofString());

        responseFuture.thenAccept(response -> {
            System.out.println(response.body());
            if (response.statusCode() == 200 && response.body().contains("\"started\":true")) {
                // Get JWT token from response headers
                System.out.println(response.headers());
                response.headers().firstValue("x-authorization").ifPresentOrElse(token -> {
                    try {
                        getRequestStatus(token, asanID, searchElement, dateStart, dateEnd, unread, makeReport);
                    } catch (URISyntaxException e) {
                        throw new RuntimeException(e);
                    }
                }, () -> System.out.println("x-authorization header not found!"));

            } else {
                System.out.println("Authentication failed!");
                uiModifier.markAsFailed(asanID);
                PROCESSED_RECORDS ++;
                FAILED_RECORDS_COUNT ++;
                checkProcessingCompletion();
            }
        });
    }


    //2
    private HttpRequest formRequest(String token, String cookieValue) throws URISyntaxException {
        URI endpoint = new URI(BASE_URL + "/auth/public/v1/asanImza/status");

        HttpRequest.Builder request = HttpRequest.newBuilder()
            .GET()
            .uri(endpoint)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:142.0) Gecko/20100101 Firefox/142.0")
            .header("x-authorization", "Bearer " + token);

        if (cookieValue != null && !cookieValue.isEmpty()){
            System.out.println("Cookie value: " + cookieValue);
            request.setHeader("JSESSIONID", cookieValue);
        } else {
            System.out.println("Cookie value is empty ... skipping ...");
        }

        return request.build();
    }

    //3
    protected void getCertificates(String cookieValue, String token,
                                   String asanID, String searchElement, String dateStart, String dateEnd,
                                   boolean unread, boolean makeReport) throws URISyntaxException {
        URI endpoint = new URI(BASE_URL + "/auth/public/v1/asanImza/certificates");

        HttpRequest request = HttpRequest.newBuilder()
            .GET()
            .uri(endpoint)
            .header("x-authorization", "Bearer " + token)
            .header("JSESSIONID", cookieValue)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:142.0) Gecko/20100101 Firefox/142.0")
            .build();

        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenAccept(response -> {
                System.out.println("Tried to get certificates ...");
                System.out.println("Response ...");
                System.out.println(response.statusCode());

                if (response.statusCode() == 200 && response.body().contains("certificates")) {
                    try {
                        ObjectMapper mapper = new ObjectMapper();
                        CertificateModel.CertificatesResponse certs = mapper.readValue(response.body(),
                                CertificateModel.CertificatesResponse.class);

                        System.out.println("Parsed certificates:");

                        System.out.println(response.body());
                        for (CertificateModel.Certificate cert : certs.getCertificates()) {
                            if (cert.getIndividualInfo() != null) {
                                System.out.println("Individual: " + cert.getIndividualInfo().getName() +
                                        " (FIN: " + cert.getIndividualInfo().getFin() + ")");
                            }
                            if (cert.getLegalInfo() != null) {
                                System.out.println("Legal: " + cert.getLegalInfo().getName() +
                                        " (TIN: " + cert.getLegalInfo().getTin() + ")");
                            }
                            System.out.println("Position: " + cert.getPosition());
                            System.out.println("Has access: " + cert.isHasAccess());
                            System.out.println("------");

                            System.err.println("I am searching for element: " + searchElement);
                            System.out.println("Here is legal info bro i need it");
                            System.out.println(cert.getLegalInfo());

                            if (cert.getLegalInfo() != null && cert.getLegalInfo().getName()
                                    .toLowerCase(Locale.ROOT).contains(searchElement.toLowerCase(Locale.ROOT))) {

                                System.out.println("Trying to choose a tax payer based on SEARCH_ELEMENT");
                                System.out.println(searchElement);

                                response.headers()
                                    .firstValue("Set-Cookie")
                                    .ifPresentOrElse(s -> {
                                        if (s.contains("JSESSIONID")){
                                            System.out.println("Cookie value: " + s);
                                            try {
                                                chooseTaxPayer(cert.getLegalInfo().getTin(), cookieValue, token,
                                                        asanID, searchElement, dateStart, dateEnd, unread, makeReport);
                                            } catch (URISyntaxException e) {
                                                throw new RuntimeException(e);
                                            }
                                        }else{
                                            System.out.println("Cookie containing JSESSIONID not found!");
                                            uiModifier.markAsFailed(asanID);
                                            PROCESSED_RECORDS ++;
                                            FAILED_RECORDS_COUNT ++;
                                            checkProcessingCompletion();

                                        }

                                    }, () -> {
                                        System.out.println("Cookies not found !!");
                                        uiModifier.markAsFailed(asanID);
                                        PROCESSED_RECORDS ++;
                                        FAILED_RECORDS_COUNT ++;
                                        checkProcessingCompletion();
                                    });

                            }else{
                                System.out.println("Could not find tax payer's certificate ...");
                                uiModifier.markAsFailed(asanID);
                                PROCESSED_RECORDS ++;
                                FAILED_RECORDS_COUNT ++;
                                checkProcessingCompletion();
                            }

                        }
                    } catch (Exception e) {
                        uiModifier.markAsFailed(asanID);
                        PROCESSED_RECORDS ++;
                        System.err.println(e.getMessage());
                        FAILED_RECORDS_COUNT ++;
                        checkProcessingCompletion();
                    }
                }
            });
    }

    //4
    protected void chooseTaxPayer(
            String tin, String cookieValue, String token, String asanID,
            String searchElement, String dateStart, String dateEnd, boolean unread, boolean makeReport
    ) throws URISyntaxException {
        URI endpoint = new URI(BASE_URL + "/auth/public/v1/asanImza/chooseTaxpayer");

        System.out.println("Token when choosing taxpayer ..." + token);
        System.out.println("Cookie when choosing taxpayer ..." + cookieValue);
        System.out.println("TIN " + tin);

        String body = String.format("{\"ownerType\":\"legal\",\"legalTin\":\"%s\"}", tin);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(endpoint)
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .header("Cookie", cookieValue)
            .header("x-authorization", "Bearer " + token)
            .header("Content-Type", "application/json")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:142.0) Gecko/20100101 Firefox/142.0")
            .build();

        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenAccept(response -> {
                System.out.println("Tried to choose tax payer ...");
                System.out.println("Response status: " + response.statusCode());
                System.out.println("Response body: " + response.body());
                System.out.println("Response headers: " + response.headers());

                if (response.statusCode() == 200) {
                    AtomicReference<String> cookie = new AtomicReference<>();
                    AtomicReference<String> xAuth = new AtomicReference<>();

                    response.headers().map().forEach((key, values) -> {
                        if (key.equalsIgnoreCase("set-cookie")) {
                            values.stream()
                                .filter(s -> s.contains("JSESSIONID"))
                                .findFirst()
                                .ifPresent(s -> cookie.set(s.split(";")[0]));
                        }
                        if (key.equalsIgnoreCase("x-authorization")) {
                            values.stream()
                                .findFirst()
                                .ifPresent(xAuth::set);
                        }
                    });

                    if (cookie.get() != null && xAuth.get() != null) {
                        System.out.println("Chose tax payer successfully!");
                        try {
                            getInboxMessages(cookie.get(), xAuth.get(), asanID, searchElement, dateStart,
                                    dateEnd, unread, makeReport);
                        } catch (URISyntaxException e) {
                            uiModifier.markAsFailed(asanID);
                            PROCESSED_RECORDS ++;
                            FAILED_RECORDS_COUNT ++;
                            checkProcessingCompletion();
                            throw new RuntimeException(e);
                        }

                    } else {
                        uiModifier.markAsFailed(asanID);
                        PROCESSED_RECORDS ++;
                        FAILED_RECORDS_COUNT ++;
                        checkProcessingCompletion();
                        System.out.println("Error while choosing taxpayer ... Null values detected ...");
                    }
                }}
            );
    }

    //5
    protected void getInboxMessages(
            String cookieValue, String jwtToken, String asanID, String searchElement,
            String dateStart, String dateEnd, boolean unread, boolean makeReport
    ) throws URISyntaxException {
        URI endpoint = new URI(BASE_URL + "/edi/public/v1/message/find.inbox");
        String body = """
            {
                "correspondent": {
                    "kind": "taxauthority",
                    "tin": null,
                    "taxAuthorityCode": null, 
                    "name": null,
                    "fin": null
                },
                "offset": 0,
                "maxCount": 100,
                "containsText": "",
                "categoryCodes": [],
                "docGroups": null,
                "unreadOnly": %s,
                "registerNumber": null,
                "withActionOnly": null,
                "withAttachmentsOnly": null,
                "startDate": "%s",
                "endDate": "%s"
            }""".formatted(!unread ? "null" : true, dateStart, dateEnd);

            System.out.println("My request to get the inbox ...");
            System.out.println(body);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(endpoint)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(15))
                .header("Cookie", cookieValue)
                .header("x-authorization", "Bearer " + jwtToken)
                .header("Content-Type", "application/json")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:142.0) Gecko/20100101 Firefox/142.0")
                .build();

            client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    System.out.println("Tried to get inbox messages...");
                    System.out.println("Response body: " + response.body());

                    if (response.statusCode() == 200) {
                        System.out.println("INBOX MESSAGES ARE OK!!");

                        ObjectMapper mapper = new ObjectMapper();
                        try {
                            DocumentModel documentModel = mapper.readValue(response.body(), DocumentModel.class);
                            System.out.println("Messages: " + documentModel.getMessages().size());
                            System.out.println("Has more: " + documentModel.getHasMore());
                            String savePath = prefs.get("folderSavePath", null);

                            if (savePath == null){
                                System.err.println("SavePath is null!!");
                                uiModifier.markAsFailed(asanID);
                                PROCESSED_RECORDS ++;
                                FAILED_RECORDS_COUNT ++;
                                checkProcessingCompletion();
                                return;
                            }

                            response.headers()
                                .firstValue("Set-Cookie")
                                .ifPresentOrElse(s -> {
                                    if (s.contains("JSESSIONID")){
                                        System.out.println("Cookie value: " + s);

                                        if (makeReport){

                                            String cookie = downloadAndSaveReport(jwtToken, s, asanID,
                                                searchElement, dateStart.replace("-", "/"),
                                                dateEnd.replace("-", "/"));

                                            for (DocumentModel.Document document: documentModel.getMessages()){
                                                System.out.println("Passing data to inbox messages with ID: " + document.getId());
                                                getFileInsideInboxMessage(document.getId(), cookie, jwtToken, asanID, searchElement);
                                            }

                                        }else{

                                            for (DocumentModel.Document document: documentModel.getMessages()){
                                                System.out.println("Passing data to inbox messages with ID: " + document.getId());
                                                getFileInsideInboxMessage(document.getId(), s, jwtToken, asanID, searchElement);
                                            }

                                        }
                                }else{
                                        System.out.println("Cookie containing JSESSIONID not found!");
                                        uiModifier.markAsFailed(asanID);
                                        PROCESSED_RECORDS ++;
                                        FAILED_RECORDS_COUNT ++;
                                        checkProcessingCompletion();

                                    }

                                }, () -> {
                                    System.out.println("Cookies not found !!");
                                    uiModifier.markAsFailed(asanID);
                                    PROCESSED_RECORDS ++;
                                    FAILED_RECORDS_COUNT ++;
                                    checkProcessingCompletion();
                                });
                        } catch (JsonProcessingException e) {
                            uiModifier.markAsFailed(asanID);
                            PROCESSED_RECORDS ++;
                            FAILED_RECORDS_COUNT ++;
                            checkProcessingCompletion();
                            System.out.println("Could not deserialize Document object , sorry .....");
                            throw new RuntimeException(e);
                        }

                    } else {
                        uiModifier.markAsFailed(asanID);
                        PROCESSED_RECORDS ++;
                        FAILED_RECORDS_COUNT ++;
                        checkProcessingCompletion();
                        System.out.println("Error while choosing inbox messages ...");
                    }
                });
    }

    //6
private void getFileInsideInboxMessage(String inboxID, String cookieValue, String jwtToken, String asanID, String searchElement){

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL + "/edi/public/v1/message/%s?sourceSystem=avis".formatted(inboxID)))
            .GET()
            .timeout(Duration.ofSeconds(15))
            .header("Cookie", cookieValue)
            .header("x-authorization", "Bearer " + jwtToken)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:142.0) Gecko/20100101 Firefox/142.0")
            .build();

        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenAccept(response -> {
                System.out.println("Tried to get data inside inbox");
                System.out.println("Response body: " + response.body());

                if (response.statusCode() == 200) {
                    System.out.println("INBOX DATA IS OK!!");
                    ObjectMapper mapper = new ObjectMapper();

                    try {
                        MessageDto documentModel = mapper.readValue(response.body(), MessageDto.class);
                        System.out.println("Document model: " + documentModel);

                        String filePart = documentModel.getSubject() != null ?
                                documentModel.getSubject() : documentModel.getContent();


                        //finally iterate through available files and download 'em
                        for (MessageDto.FileDto file: documentModel.getFiles()){
                            String fileExtension = file.getName().substring(file.getName().lastIndexOf(".") + 1);

                            if (documentModel.getFiles().size() == 1){
                                System.out.println("Passing data to download document with ID: " + file.getId());
                                String fileName = filePart + "." + fileExtension;
                                downloadAndSaveDocument(file.getId(), fileName, jwtToken, asanID, searchElement);
                            }else{
                                String fileName = filePart + UUID.randomUUID() + "." + fileExtension;
                                downloadAndSaveDocument(file.getId(), fileName, jwtToken, asanID, searchElement);
                            }

                        }

                    } catch (JsonProcessingException e) {
                        uiModifier.markAsFailed(asanID);
                        PROCESSED_RECORDS ++;
                        FAILED_RECORDS_COUNT ++;
                        checkProcessingCompletion();
                        System.out.println("Could not deserialize Document object , sorry .....");
                        throw new RuntimeException(e);
                    }

                } else {
                    uiModifier.markAsFailed(asanID);
                    PROCESSED_RECORDS ++;
                    FAILED_RECORDS_COUNT ++;
                    checkProcessingCompletion();
                    System.out.println("Error while choosing inbox messages ...");
                }
            });

    }

    //7
    private void downloadAndSaveDocument(String documentID, String fileName, String jwtToken,
                                         String asanID, String searchElement) {
        System.out.println("I am reading documents ...");
        System.out.println("Filename: " + fileName);

        String savePath = prefs.get("folderSavePath", null);

        if (savePath == null){
            System.err.println("Upload path is null!!");
            uiModifier.markAsFailed(asanID);
            PROCESSED_RECORDS ++;
            FAILED_RECORDS_COUNT ++;
            checkProcessingCompletion();
            return;
        }

        Path dirPath = createUploadDir(savePath, searchElement);

        if (dirPath == null){
            System.err.println("SavePath is null!!");
            uiModifier.markAsFailed(asanID);
            PROCESSED_RECORDS ++;
            FAILED_RECORDS_COUNT ++;
            checkProcessingCompletion();
            return;
        }

        Path filePath = dirPath.resolve(fileName);

        HttpRequest fileReq = HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL + "/filestorage/public/v1/download/%s?type=avis".formatted(documentID)))
            .header("x-authorization", "Bearer " + jwtToken)
            .header("Accept", "application/json, text/plain, */*")
            .header("Accept-Encoding", "gzip, deflate, br")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:142.0) Gecko/20100101 Firefox/142.0")
            .header("Referer", "https://new.e-taxes.gov.az/eportal/messages/view/" + documentID)
            .GET()
            .build();

        client.sendAsync(fileReq, HttpResponse.BodyHandlers.ofFile(filePath,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE
            ))
        .thenAccept(fileResponse -> {
            if (fileResponse.statusCode() == 200) {
                System.out.println("File downloaded successfully to: " + filePath);
                uiModifier.markAsCompleted(asanID);
                PROCESSED_RECORDS ++;
                COMPLETED_RECORDS_COUNT ++;
                checkProcessingCompletion();
                try {
                    logout(jwtToken);
                } catch (URISyntaxException e) {
                    System.err.println(e.getMessage());
                    throw new RuntimeException(e);
                }
            }
        });
    }

    //8
private String downloadAndSaveReport(String jwtToken, String cookieValue, String asanID, String searchElement,
                                        String startDate, String endDate) {
        System.out.println("Saving report ...");

        String savePath = prefs.get("folderSavePath", null);

        if (savePath == null){
            System.err.println("Upload path is null!!");
            uiModifier.markAsFailed(asanID);
            PROCESSED_RECORDS ++;
            FAILED_RECORDS_COUNT ++;
            checkProcessingCompletion();
            return null;
        }

        Path dirPath = createUploadDir(savePath, searchElement);
        Path newPath = createUploadDir(dirPath.toString(), "personal-report");

        if (newPath == null) {
            System.err.println("One of path is null!!!");
            uiModifier.markAsFailed(asanID);
            PROCESSED_RECORDS++;
            FAILED_RECORDS_COUNT++;
            checkProcessingCompletion();
            return null;
        }

        Path filePath = newPath.resolve("report.pdf");

        HttpRequest fileReq = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL +
                        ("/budget/public/v1/operations/shv/extract/pdf?=&startDate=%s&endDate=%s&amountType=1&" +
                            "reportType=2&format=pdf&detail=0&customBalance=0")
                            .formatted(startDate, endDate)))
                .header("x-authorization", "Bearer " + jwtToken)
                .headers("Cookie", cookieValue)
                .header("Accept", "application/json, text/plain, */*")
                .header("Accept-Encoding", "gzip, deflate, br")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:142.0) Gecko/20100101 Firefox/142.0")
                .GET()
                .build();

        CompletableFuture<String> cookieFuture = new CompletableFuture<>();

        client.sendAsync(fileReq, HttpResponse.BodyHandlers.ofFile(filePath,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE
            ))
            .thenAccept(fileResponse -> {
                if (fileResponse.statusCode() == 200) {
                    System.out.println("File downloaded successfully to: " + filePath);
                    uiModifier.markAsCompleted(asanID);
                    PROCESSED_RECORDS ++;
                    COMPLETED_RECORDS_COUNT ++;

                    fileResponse.headers().firstValue("Set-Cookie").ifPresentOrElse(s -> {
                        if (s.contains("JSESSIONID")){
                            cookieFuture.complete(s);
                        }else{
                            cookieFuture.complete("");
                            System.out.println("Cookie not found!!");
                        }
                    }, () -> {
                        cookieFuture.complete("");
                        System.out.println("No cookie found");
                    });
                }
            });

        try {
            return cookieFuture.get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            System.err.println("Error getting cookie: " + e.getMessage());
            return "";
        }
    }

    protected void prepareApiCalls(int recordSize, String number, String id, String search,
                                   String startDate, String endDate, boolean oxunmamis, boolean makeReport){

        try {
            authenticateClient(number, id, search, startDate, endDate, oxunmamis, makeReport);
            TOTAL_RECORDS_COUNT = recordSize;
        } catch (URISyntaxException e) {
            uiModifier.markAsFailed(id);
            FAILED_RECORDS_COUNT ++;
            checkProcessingCompletion();
            throw new RuntimeException(e);
        }
    }

    //6
    protected void logout(String token
                          //,String cookie
    ) throws URISyntaxException{
        URI endpoint = new URI(BASE_URL + "/auth/public/v1/legacyLogout");

        HttpRequest request = HttpRequest.newBuilder()
            .GET()
            .uri(endpoint)
            .header("x-authorization", token)
//            .header("JSESSIONID", cookie)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:142.0) Gecko/20100101 Firefox/142.0")
            .build();

        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenAccept(response -> {
                if (response.statusCode() == 200 && response.body().contains("certificates")) {
                    System.out.println("LOGGED OUT SUCCESSFULLY!");
                }else{
                    System.out.println("Could not log out!!");
                }
            });

    }

}