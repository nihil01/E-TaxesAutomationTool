package com.bizcon.taxesautomator.services;

import com.bizcon.taxesautomator.controllers.MainController;
import com.bizcon.taxesautomator.models.CertificateModel;
import com.bizcon.taxesautomator.models.DocumentModel;
import com.bizcon.taxesautomator.models.MessageDto;
import com.bizcon.taxesautomator.utils.ApiUtils;
import com.bizcon.taxesautomator.utils.MessageType;
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
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.prefs.Preferences;

public class ApiService implements ApiUtils {
    private final HttpClient client;
    private final String BASE_URL = "https://new.e-taxes.gov.az/api/po";
    private final Preferences prefs = Preferences.userNodeForPackage(MainController.class);
    private AtomicInteger PROCESSED_RECORDS = new AtomicInteger(0);
    private AtomicInteger TOTAL_RECORDS_COUNT = new AtomicInteger(0);
    private AtomicBoolean isCompletionNotified = new AtomicBoolean(false);

    private AtomicInteger FAILED_RECORDS_COUNT = new AtomicInteger(0);
    private AtomicInteger COMPLETED_RECORDS_COUNT = new AtomicInteger(0);

    protected UiModifier uiModifier;

    protected ApiService() {
        System.out.println("ApiService created!");
        client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(15))
            .build();
    }

    private void checkProcessingCompletion() {
        System.out.println("Checking completion - Processed: " + PROCESSED_RECORDS.get() + ", Total: " + TOTAL_RECORDS_COUNT.get());
        if (PROCESSED_RECORDS.get() >= TOTAL_RECORDS_COUNT.get() && isCompletionNotified.compareAndSet(false, true)) {
            uiModifier.notifyCompletion(
                    "Emeliyyat ugurla bitdi!\n Ugurlu: %s | Ugursuz: %s"
                            .formatted(COMPLETED_RECORDS_COUNT.get(), FAILED_RECORDS_COUNT.get())
            );
        }
    }

    protected void getRequestStatus(
            String token, String asanID, String searchElement,
            String dateStart, String dateEnd, boolean unread, boolean makeReport, boolean detailReport
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
                    FAILED_RECORDS_COUNT.incrementAndGet();
                    PROCESSED_RECORDS.incrementAndGet();
                    checkProcessingCompletion();
                    return;
                }

                if (!firstRequestTriggered[0]) {
                    System.out.println("First request triggered");
                    GetAPIStatusState(firstRequest, timer, cookieValue, token, asanID, searchElement,
                            dateStart, dateEnd, unread, makeReport, detailReport);
                    firstRequestTriggered[0] = true;
                } else {
                    try {
                        HttpRequest newRequest = formRequest(token, cookieValue.get());
                        GetAPIStatusState(newRequest, timer, cookieValue, token, asanID, searchElement,
                                dateStart, dateEnd, unread, makeReport, detailReport);
                    } catch (URISyntaxException e) {
                        timer.cancel();
                        FAILED_RECORDS_COUNT.incrementAndGet();
                        PROCESSED_RECORDS.incrementAndGet();
                        checkProcessingCompletion();
                    }
                }

                retryCount[0]++;
            }
        }, 0, 5000);
    }




    private void GetAPIStatusState(
            HttpRequest request, Timer timer, AtomicReference<String> cookieValue, String token,
            String asanID, String searchElement, String dateStart, String dateEnd,
            boolean unread, boolean makeReport, boolean detailReport
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
                                dateStart, dateEnd, unread, makeReport, detailReport);
                    } catch (URISyntaxException e) {
                        uiModifier.markAsFailed(asanID);
                        FAILED_RECORDS_COUNT.incrementAndGet();
                        PROCESSED_RECORDS.incrementAndGet();
                        checkProcessingCompletion();
                        throw new RuntimeException(e);
                    }
                }

                cookieValue.set(response.headers()
                    .firstValue("Set-Cookie")
                    .map(value -> value.split(";")[0])
                    .orElse(null));
            })
            .exceptionally(ex -> {
                System.err.println("Async error: " + ex.getMessage());
                uiModifier.markAsFailed(asanID);
                FAILED_RECORDS_COUNT.incrementAndGet();
                PROCESSED_RECORDS.incrementAndGet();
                checkProcessingCompletion();
                return null;
            });
    }

    //1
    protected void authenticateClient(
            String phone, String asanID,
            String searchElement, String dateStart, String dateEnd, boolean unread, boolean makeReport, boolean detailReport
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
                        getRequestStatus(token, asanID, searchElement, dateStart, dateEnd, unread, makeReport, detailReport);
                    } catch (URISyntaxException e) {
                        throw new RuntimeException(e);
                    }
                }, () -> System.out.println("x-authorization header not found!"));

            } else {
                System.out.println("Authentication failed!");
                uiModifier.markAsFailed(asanID);
                FAILED_RECORDS_COUNT.incrementAndGet();
                PROCESSED_RECORDS.incrementAndGet();
                checkProcessingCompletion();
            }
        }).exceptionally(throwable -> {
            System.err.println("Async error: " + throwable.getMessage());
            uiModifier.markAsFailed(asanID);
            FAILED_RECORDS_COUNT.incrementAndGet();
            PROCESSED_RECORDS.incrementAndGet();
            checkProcessingCompletion();
            return null;
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
                                   boolean unread, boolean makeReport, boolean detailReport) throws URISyntaxException {
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
                                                        asanID, searchElement, dateStart, dateEnd, unread, makeReport,
                                                        detailReport);
                                            } catch (URISyntaxException e) {
                                                throw new RuntimeException(e);
                                            }
                                        }else{
                                            System.out.println("Cookie containing JSESSIONID not found!");
                                            uiModifier.markAsFailed(asanID);
                                            FAILED_RECORDS_COUNT.incrementAndGet();
                                            PROCESSED_RECORDS.incrementAndGet();
                                            checkProcessingCompletion();

                                        }

                                    }, () -> {
                                        System.out.println("Cookies not found !!");
                                        uiModifier.markAsFailed(asanID);
                                        FAILED_RECORDS_COUNT.incrementAndGet();
                                        PROCESSED_RECORDS.incrementAndGet();
                                        checkProcessingCompletion();
                                    });

                            }else{
                                System.out.println("Could not find tax payer's certificate ...");
                                uiModifier.markAsFailed(asanID);
                                FAILED_RECORDS_COUNT.incrementAndGet();
                                PROCESSED_RECORDS.incrementAndGet();
                                checkProcessingCompletion();
                            }

                        }
                    } catch (Exception e) {
                        uiModifier.markAsFailed(asanID);
                        FAILED_RECORDS_COUNT.incrementAndGet();
                        PROCESSED_RECORDS.incrementAndGet();
                        checkProcessingCompletion();
                    }
                }
            })
            .exceptionally(ex -> {
                System.err.println("Async error: " + ex.getMessage());
                uiModifier.markAsFailed(asanID);
                FAILED_RECORDS_COUNT.incrementAndGet();
                PROCESSED_RECORDS.incrementAndGet();
                checkProcessingCompletion();
                return null;
            });
    }

    //4
    protected void chooseTaxPayer(
            String tin, String cookieValue, String token, String asanID,
            String searchElement, String dateStart, String dateEnd,
            boolean unread, boolean makeReport, boolean detailReport
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
                        //Firstly, get reports for the taxpayer
                        CompletableFuture<String> afterReports = makeReport
                            ? downloadAndSaveReportAsync(xAuth.get(), cookie.get(), searchElement, detailReport, MessageType.PDF)
                            .thenCompose(c1 -> downloadAndSaveReportAsync(xAuth.get(), c1, searchElement, detailReport, MessageType.EXCEL))
                            .thenCompose(c2 -> downloadAndSaveReportAsync(xAuth.get(), c2, searchElement, detailReport, MessageType.HTML))
                            : CompletableFuture.completedFuture(cookie.get());

                        CompletableFuture<Void> allWork = afterReports
                            .thenCompose(c -> {
                                try {
                                    return getMessages(c, xAuth.get(), searchElement, dateStart, dateEnd, unread, MessageType.INBOX);
                                } catch (URISyntaxException e) {
                                    return CompletableFuture.failedFuture(e);
                                }
                            })
                            .thenCompose(c2 -> {
                                try {
                                    return getMessages(c2, xAuth.get(), searchElement, dateStart, dateEnd, unread, MessageType.OUTBOX);
                                } catch (URISyntaxException e) {
                                    return CompletableFuture.failedFuture(e);
                                }
                            })
                            .thenApply(c3 -> null);

                        allWork.whenComplete((ok, ex) -> {
                            if (ex == null) {
                                uiModifier.markAsCompleted(asanID);
                                COMPLETED_RECORDS_COUNT.incrementAndGet();
                            } else {
                                uiModifier.markAsFailed(asanID);
                                FAILED_RECORDS_COUNT.incrementAndGet();
                            }
                            PROCESSED_RECORDS.incrementAndGet();
                            checkProcessingCompletion();
                        });

                    } else {
                        uiModifier.markAsFailed(asanID);
                        FAILED_RECORDS_COUNT.incrementAndGet();
                        PROCESSED_RECORDS.incrementAndGet();
                        checkProcessingCompletion();
                        System.out.println("Error while choosing taxpayer ... Null values detected ...");
                    }
                }}
            )
            .exceptionally(ex -> {
                System.err.println("Async error: " + ex.getMessage());
                uiModifier.markAsFailed(asanID);
                FAILED_RECORDS_COUNT.incrementAndGet();
                PROCESSED_RECORDS.incrementAndGet();
                checkProcessingCompletion();
                return null;
            });
    }
    //https://new.e-taxes.gov.az/api/po/edi/public/v1/message/find.inbox
    //https://new.e-taxes.gov.az/api/po/edi/public/v1/message/find.outbox
    //5
    protected CompletableFuture<String> getMessages(
            String cookieValue, String jwtToken, String searchElement,
            String dateStart, String dateEnd, boolean unread, MessageType msg
    ) throws URISyntaxException {

        final String typePath = (msg == MessageType.INBOX) ? "find.inbox" : "find.outbox";
        final URI endpoint = new URI(BASE_URL + "/edi/public/v1/message/" + typePath);
        String body;

        if (msg == MessageType.INBOX){
                body = """
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

        }else{

                body = """
            {
                "correspondent": {
                    "kind": "taxauthority",
                    "tin": null,
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
        }

        System.out.println("Request " + typePath + " ...");
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

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenCompose(response -> {
                    System.out.println("Messages response " + typePath + ": " + response.statusCode());
                    if (response.statusCode() != 200) {
                        return CompletableFuture.failedFuture(new IllegalStateException("HTTP " + response.statusCode() + " for " + typePath));
                    }

                    // Подхватываем обновлённую куку; если нет — используем входящую
                    String nextCookie = response.headers().firstValue("Set-Cookie")
                            .filter(s -> s.contains("JSESSIONID"))
                            .map(s -> s.split(";")[0])
                            .orElse(cookieValue);

                    ObjectMapper mapper = new ObjectMapper();
                    final DocumentModel documentModel;
                    try {
                        documentModel = mapper.readValue(response.body(), DocumentModel.class);
                    } catch (JsonProcessingException e) {
                        return CompletableFuture.failedFuture(e);
                    }

                    System.out.println("Messages count: " +
                            (documentModel.getMessages() == null ? 0 : documentModel.getMessages().size())
                    );

                    System.out.println("Messages");
                    System.out.println(documentModel.getMessages());

                    // Если сообщений нет — возвращаем текущую (возможно обновлённую) куку
                    if (documentModel.getMessages() == null || documentModel.getMessages().isEmpty()) {
                        return CompletableFuture.completedFuture(nextCookie);
                    }

                    // Последовательная цепочка: каждый message может вернуть новую куку
                    CompletableFuture<String> chain = CompletableFuture.completedFuture(nextCookie);
                    for (DocumentModel.Document doc : documentModel.getMessages()) {
                        chain = chain.thenCompose(curCookie ->
                            getFileInsideMessageAsync(doc.getId(), curCookie, jwtToken, searchElement, msg)
                                .thenApply(updatedCookie -> {
                                    // fallback на текущую куку, если метод вернул пустую
                                    return (updatedCookie != null && !updatedCookie.isBlank()) ? updatedCookie : curCookie;
                                })
                        );
                    }
                    return chain;
                })
                .exceptionally(ex -> {
                    throw new java.util.concurrent.CompletionException(ex);
                });
    }

    //6
    private CompletableFuture<String> getFileInsideMessageAsync(
            String inboxID, String cookieValue, String jwtToken, String searchElement, MessageType msg) {

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/edi/public/v1/message/%s?sourceSystem=avis".formatted(inboxID)))
                .GET()
                .timeout(Duration.ofSeconds(15))
                .header("Cookie", cookieValue)
                .header("x-authorization", "Bearer " + jwtToken)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:142.0) Gecko/20100101 Firefox/142.0")
                .build();

        CompletableFuture<String> promise = new CompletableFuture<>();
        ObjectMapper mapper = new ObjectMapper();

        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    if (response.statusCode() != 200) {
                        promise.completeExceptionally
                            (
                                new IllegalStateException("Inbox item fetch failed: " + response.statusCode())
                            );
                        return;
                    }

                    String newCookie = response.headers().firstValue("Set-Cookie")
                            .filter(s -> s.contains("JSESSIONID"))
                            .map(s -> s.split(";")[0])
                            .orElse(cookieValue);

                    List<CompletableFuture<Void>> fileTasks = new java.util.ArrayList<>();
                    try {
                        MessageDto dto = mapper.readValue(response.body(), MessageDto.class);
                        String filePart = dto.getSubject() != null ? dto.getSubject() : dto.getContent();

                        for (MessageDto.FileDto file: dto.getFiles()) {
                            String ext = file.getName().substring(file.getName().lastIndexOf(".") + 1);
                            String rawName = (dto.getFiles().size() == 1)
                                    ? (filePart + "." + ext)
                                    : (filePart + java.util.UUID.randomUUID() + "." + ext);

                            String fileName = sanitizeForWindows(rawName);

                            fileTasks.add(downloadAndSaveDocumentAsync(file.getId(), fileName,
                                    jwtToken, searchElement, msg));
                        }

                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                    CompletableFuture.allOf(fileTasks.toArray(new CompletableFuture[0]))
                            .whenComplete((v, ex) -> {
                                if (ex == null) promise.complete(newCookie);
                                else promise.completeExceptionally(ex);
                            });
                })
                .exceptionally(ex -> { promise.completeExceptionally(ex); return null; });

        return promise;
    }

    private CompletableFuture<String> downloadAndSaveReportAsync(
            String jwtToken, String cookieValue,
            String searchElement, boolean details, MessageType msg) {

        LocalDate now = LocalDate.now();
        String startDate = String.format("%04d/%02d/%02d", now.getYear(), 1, 1);
        String endDate   = String.format("%04d/%02d/%02d", now.getYear(), now.getMonthValue(), now.getDayOfMonth());

        String savePath = prefs.get("folderSavePath", null);
        if (savePath == null){
            return CompletableFuture.failedFuture(new IllegalStateException("Upload path is null"));
        }

        Path dirPath = createUploadDir(savePath, searchElement);
        if (dirPath == null){
            return CompletableFuture.failedFuture(new IllegalStateException("SavePath is null"));
        }

        Path filePath;
        if (msg == MessageType.PDF){
            filePath = dirPath.resolve("SHV.pdf");
        } else if (msg == MessageType.EXCEL){
            filePath = dirPath.resolve("SHV.xls");
        } else {
            filePath = dirPath.resolve("SHV.html");
        }

        final String fmt = (msg == MessageType.PDF) ? "pdf" : (msg == MessageType.EXCEL ? "excel" : "html");
        String url = BASE_URL + "/budget/public/v1/operations/shv/extract/%s?=&startDate=%s&endDate=%s&amountType=1&reportType=2&format=%s&detail=%d&customBalance=1"
                .formatted(fmt, startDate, endDate, fmt, details ? 1 : 0);

        HttpRequest fileReq = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMinutes(1))
                .header("x-authorization", "Bearer " + jwtToken)
                .header("Cookie", cookieValue)
                .header("Accept", (msg == MessageType.PDF) ? "application/pdf"
                        : (msg == MessageType.EXCEL ? "application/vnd.ms-excel" : "text/html"))
                .header("Accept-Encoding", "gzip, deflate, br")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:142.0) Gecko/20100101 Firefox/142.0")
                .GET()
                .build();

        return client.sendAsync(
                fileReq,
                HttpResponse.BodyHandlers.ofFile(filePath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)
            )
            .thenApply(fileResponse -> {
                if (fileResponse.statusCode() != 200) {
                    throw new IllegalStateException("Report download failed: " + fileResponse.statusCode());
                }
                System.out.println("Report downloaded successfully to: " + filePath);

                return fileResponse.headers().allValues("Set-Cookie").stream()
                    .filter(s -> s.contains("JSESSIONID"))
                    .map(s -> s.split(";")[0])
                    .findFirst()
                    .orElse(cookieValue);

            })
            .exceptionally(ex -> {
                System.err.println("Report download error: " + ex.getMessage());
                throw new java.util.concurrent.CompletionException(ex);
            });
    }

    private CompletableFuture<Void> downloadAndSaveDocumentAsync(
            String documentID, String fileName, String jwtToken, String searchElement, MessageType msg) {

        CompletableFuture<Void> promise = new CompletableFuture<>();
        System.out.println("Downloading file with fileName " + fileName);
        System.out.println("Document ID " + documentID);
        String savePath = prefs.get("folderSavePath", null);
        if (savePath == null){
            promise.completeExceptionally(new IllegalStateException("Upload path is null"));
            return promise;
        }

        Path dirPath = createUploadDir(savePath, searchElement);

        if (dirPath == null){
            promise.completeExceptionally(new IllegalStateException("dirPath is null"));
            return promise;
        }

        Path messageTypePath = createUploadDir(dirPath.toString(), msg == MessageType.INBOX ? "daxil_olanlar" : "gonderilenler");

        Path filePath = messageTypePath.resolve(fileName);
        System.out.println("Path to save a file");
        System.out.println(filePath);

        HttpRequest fileReq = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/filestorage/public/v1/download/%s?type=avis".formatted(documentID)))
                .header("x-authorization", "Bearer " + jwtToken)
                .header("Accept", "application/json, text/plain, */*")
                .header("Accept-Encoding", "gzip, deflate, br")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:142.0) Gecko/20100101 Firefox/142.0")
                .header("Referer", "https://new.e-taxes.gov.az/eportal/messages/view/" + documentID)
                .GET()
                .build();

        client.sendAsync(fileReq, HttpResponse.BodyHandlers.ofFile(
                    filePath, StandardOpenOption.CREATE, StandardOpenOption.WRITE)
                )
            .thenAccept(fileResponse -> {
                if (fileResponse.statusCode() == 200) {
                    System.out.println("File downloaded successfully to: " + filePath);
                    promise.complete(null);
                } else {
                    promise.completeExceptionally(new IllegalStateException("File download failed: " + fileResponse.statusCode()));
                }
            })
            .exceptionally(ex -> {
                promise.completeExceptionally(ex);
                return null;
            });

        return promise;
    }

    protected void prepareApiCalls(int recordSize, String number, String id, String search,
                                   String startDate, String endDate, boolean oxunmamis, boolean makeReport, boolean detailReport){

        try {
            TOTAL_RECORDS_COUNT.set(recordSize);
            authenticateClient(number, id, search, startDate, endDate, oxunmamis, makeReport, detailReport);
        } catch (URISyntaxException e) {
            uiModifier.markAsFailed(id);
            FAILED_RECORDS_COUNT.incrementAndGet();
            PROCESSED_RECORDS.incrementAndGet();
            checkProcessingCompletion();
        }
    }

    private void resetProgress() {
        PROCESSED_RECORDS.set(0);
        FAILED_RECORDS_COUNT.set(0);
        COMPLETED_RECORDS_COUNT.set(0);
        TOTAL_RECORDS_COUNT.set(0);
        isCompletionNotified.set(false);
    }

    protected void beginRun(int total) {
        resetProgress();
        TOTAL_RECORDS_COUNT.set(total);
    }

}
