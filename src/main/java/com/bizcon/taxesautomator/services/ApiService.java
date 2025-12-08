package com.bizcon.taxesautomator.services;

import com.bizcon.taxesautomator.controllers.MainController;
import com.bizcon.taxesautomator.models.CertificateModel;
import com.bizcon.taxesautomator.models.DocumentModel;
import com.bizcon.taxesautomator.models.MessageDto;
import com.bizcon.taxesautomator.utils.ApiUtils;
import com.bizcon.taxesautomator.utils.CustomHttpHandler;
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
import java.util.concurrent.*;
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

    private final HashSet<String> asanIDs = new HashSet<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    protected UiModifier uiModifier;

    protected ApiService() {
        LoggingService.logData("ApiService created!", MessageType.INFO);
        client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(15))
            .build();
    }

    private void checkProcessingCompletion() {
        LoggingService.logData("Checking completion - Processed: " + PROCESSED_RECORDS.get()
                + ", Total: " + TOTAL_RECORDS_COUNT.get(), MessageType.INFO);
        
        if (PROCESSED_RECORDS.get() >= TOTAL_RECORDS_COUNT.get() && isCompletionNotified.compareAndSet(false, true)) {
            uiModifier.notifyCompletion(
                    ("Emeliyyat bitdi!\n Ugurlu: %s | Ugursuz: %s\n")
                            .formatted(COMPLETED_RECORDS_COUNT.get(), FAILED_RECORDS_COUNT.get()),
                    new HashSet<>(asanIDs)
            );

            LoggingService.logData("Possible assanIDs to be cleared: " + asanIDs.size(), MessageType.INFO);
            LoggingService.logData("Operation finished!", MessageType.INFO);
            
        }
    }

    private CompletableFuture<Void> explicitWait() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        scheduler.schedule(() -> future.complete(null), ((int)(Math.random() * 200) + 100), TimeUnit.MILLISECONDS);
        return future;
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
                        LoggingService.logData("Browser history POST status: " + response.statusCode(), MessageType.INFO));

        HttpRequest firstRequest = formRequest(token, null);

        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                LoggingService.logData("Current retry count: " + retryCount[0], MessageType.INFO);

                if (retryCount[0] >= MAX_RETRIES) {
                    timer.cancel();
                    LoggingService.logData("Max retries reached, stopping timer", MessageType.WARN);
                    uiModifier.markAsFailed(asanID);
                    FAILED_RECORDS_COUNT.incrementAndGet();
                    PROCESSED_RECORDS.incrementAndGet();
                    checkProcessingCompletion();
                    return;
                }

                if (!firstRequestTriggered[0]) {
                    LoggingService.logData("First request triggered", MessageType.INFO);
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
                        LoggingService.logData(e.getMessage(), MessageType.ERROR);
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
                LoggingService.logData("API STATUS STA2TE: " + response.statusCode(), MessageType.INFO);
                LoggingService.logData("Intermediary API body", MessageType.INFO);
                LoggingService.logData(response.body(), MessageType.INFO);

                LoggingService.logData("API STATUS STATE RESPONSE HEADERS", MessageType.INFO);
                LoggingService.logData(response.headers().toString(), MessageType.INFO);

                if (response.statusCode() == 200 && response.body().contains("\"successful\":true")) {
                    timer.cancel();
                    try {
                        LoggingService.logData(response.body(), MessageType.INFO);
                        LoggingService.logData("REQUEST SUCCESSFUL! OBTAINING CERTIFICATES ...", MessageType.INFO);
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
                LoggingService.logData(ex.getMessage(), MessageType.ERROR);
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
            LoggingService.logData(response.body(), MessageType.INFO);
            if (response.statusCode() == 200 && response.body().contains("\"started\":true")) {
                // Get JWT token from response headers
                LoggingService.logData(response.headers().toString(), MessageType.INFO);
                response.headers().firstValue("x-authorization").ifPresentOrElse(token -> {
                    try {
                        getRequestStatus(token, asanID, searchElement, dateStart, dateEnd, unread, makeReport, detailReport);
                    } catch (URISyntaxException e) {
                        throw new RuntimeException(e);
                    }
                }, () -> LoggingService.logData("x-authorization header not found!", MessageType.WARN));

            } else {
                LoggingService.logData("Authentication failed!", MessageType.WARN);
                uiModifier.markAsFailed(asanID);
                FAILED_RECORDS_COUNT.incrementAndGet();
                PROCESSED_RECORDS.incrementAndGet();
                checkProcessingCompletion();
            }
        }).exceptionally(throwable -> {
            LoggingService.logData(throwable.getMessage(), MessageType.ERROR);
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
            LoggingService.logData("Cookie value: " + cookieValue, MessageType.INFO);
            request.setHeader("JSESSIONID", cookieValue);
        } else {
            LoggingService.logData("Cookie value is empty ... skipping ...", MessageType.WARN);
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
                LoggingService.logData("Tried to get certificates ...", MessageType.INFO);
                LoggingService.logData("Response ...", MessageType.INFO);
                LoggingService.logData(String.valueOf(response.statusCode()), MessageType.INFO);

                if (response.statusCode() == 200 && response.body().contains("certificates")) {
                    try {
                        ObjectMapper mapper = new ObjectMapper();
                        CertificateModel.CertificatesResponse certs = mapper.readValue(response.body(),
                                CertificateModel.CertificatesResponse.class);

                        LoggingService.logData("Parsed certificates:", MessageType.INFO);

                        LoggingService.logData(response.body(), MessageType.INFO);

                        boolean taxpayerFound = false;

                        for (CertificateModel.Certificate cert : certs.getCertificates()) {
                            LoggingService.logData(cert.toString(), MessageType.INFO);

                            if (cert.getIndividualInfo() != null) {
                                LoggingService.logData("Individual: " + cert.getIndividualInfo().getName() +
                                        " (FIN: " + cert.getIndividualInfo().getFin() + ")", MessageType.INFO);
                            }
                            if (cert.getLegalInfo() != null) {
                                LoggingService.logData("Legal: " + cert.getLegalInfo().getName() +
                                        " (TIN: " + cert.getLegalInfo().getTin() + ")", MessageType.INFO);
                            }
                            LoggingService.logData("Position: " + cert.getPosition(), MessageType.INFO);
                            LoggingService.logData("Has access: " + cert.isHasAccess(), MessageType.INFO);
                            LoggingService.logData("I am searching for element: " + searchElement, MessageType.INFO);

                            if (cert.getLegalInfo() != null && cert.getLegalInfo().getName()
                                    .toLowerCase(Locale.ROOT).contains(searchElement.toLowerCase(Locale.ROOT))) {

                                taxpayerFound = true;
                                LoggingService.logData("Trying to choose a tax payer based on SEARCH_ELEMENT", MessageType.INFO);
                                LoggingService.logData(searchElement, MessageType.INFO);

                                response.headers()
                                    .firstValue("Set-Cookie")
                                    .ifPresentOrElse(s -> {
                                        if (s.contains("JSESSIONID")){
                                            LoggingService.logData("Cookie value: " + s, MessageType.INFO);
                                            try {
                                                chooseTaxPayer(cert.getLegalInfo().getTin(), cookieValue, token,
                                                        asanID, searchElement, dateStart, dateEnd, unread, makeReport,
                                                        detailReport);
                                            } catch (URISyntaxException e) {
                                                LoggingService.logData(e.getMessage(), MessageType.ERROR);
                                                uiModifier.markAsFailed(asanID);
                                                FAILED_RECORDS_COUNT.incrementAndGet();
                                                PROCESSED_RECORDS.incrementAndGet();
                                                checkProcessingCompletion();;
                                            }
                                        }else{
                                            LoggingService.logData("Cookie containing JSESSIONID not found!",
                                                    MessageType.WARN);
                                        }
                                    }, () -> {
                                        LoggingService.logData("Cookies not found !!", MessageType.WARN);
                                    });
                            }
                        }

                        if (!taxpayerFound) {
                            LoggingService.logData("Could not find tax payer's certificate ...", MessageType.WARN);
                            uiModifier.markAsFailed(asanID);
                            FAILED_RECORDS_COUNT.incrementAndGet();
                            PROCESSED_RECORDS.incrementAndGet();
                            checkProcessingCompletion();
                        }

                    } catch (Exception e) {
                        LoggingService.logData(e.getMessage(), MessageType.ERROR);
                        uiModifier.markAsFailed(asanID);
                        FAILED_RECORDS_COUNT.incrementAndGet();
                        PROCESSED_RECORDS.incrementAndGet();
                        checkProcessingCompletion();
                    }
                }
            })
            .exceptionally(ex -> {
                LoggingService.logData(ex.getMessage(), MessageType.ERROR);
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

        LoggingService.logData("Token when choosing taxpayer ...", MessageType.INFO);
        LoggingService.logData("Cookie when choosing taxpayer ..." + cookieValue, MessageType.INFO);
        LoggingService.logData("TIN " + tin, MessageType.INFO);

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
                LoggingService.logData("Tried to choose tax payer ...", MessageType.INFO);
                LoggingService.logData("Response status: " + response.statusCode(), MessageType.INFO);
                LoggingService.logData("Response body: " + response.body(), MessageType.INFO);
                LoggingService.logData("Response headers: " + response.headers(), MessageType.INFO);

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
                        LoggingService.logData("Chose tax payer successfully!", MessageType.INFO);
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
                                asanIDs.add(asanID);
                            } else if (ex.getMessage().contains("HTTP 404")){
                                LoggingService.logData(ex.getMessage() +"\n 404 error found," +
                                        " but i mark it as completed", MessageType.WARN);
                                uiModifier.markAsCompleted(asanID);
                                COMPLETED_RECORDS_COUNT.incrementAndGet();
                                asanIDs.add(asanID);
                            }else{
                                LoggingService.logData(ex.getMessage(), MessageType.ERROR);
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
                        LoggingService.logData("Error while choosing taxpayer ... Null values detected ...", MessageType.WARN);
                    }
                }}
            )
            .exceptionally(ex -> {
                LoggingService.logData(ex.getMessage(), MessageType.ERROR);
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

        } else {
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

        LoggingService.logData("Request " + typePath + "...", MessageType.INFO);
        LoggingService.logData(body, MessageType.INFO);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(endpoint)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(15))
                .header("Cookie", cookieValue)
                .header("x-authorization", "Bearer " + jwtToken)
                .header("Content-Type", "application/json")
                .header("User-Agent", "Mozilla/5.0")
                .build();

        return client.sendAsync(request, new CustomHttpHandler<>(DocumentModel.class))
                .thenCompose(response -> {

                    LoggingService.logData("Messages response " + typePath + ": " + response.statusCode(), MessageType.INFO);

                    final String nextCookie = response.headers().firstValue("Set-Cookie")
                            .filter(s -> s.contains("JSESSIONID"))
                            .map(s -> s.split(";")[0])
                            .orElse(cookieValue);

                    DocumentModel documentModel = response.body();

                    if (documentModel == null || documentModel.getMessages() == null) {
                        return CompletableFuture.completedFuture(nextCookie);
                    }

                    List<DocumentModel.Document> documentList = new ArrayList<>(documentModel.getMessages());

                    // recursion download
                    CompletableFuture<List<DocumentModel.Document>> allDocsFuture =
                            documentModel.getHasMore()
                                    ? fetchAllDocuments(jwtToken, nextCookie, endpoint.toString(), msg, dateStart, dateEnd, unread, 100, documentList)
                                    : CompletableFuture.completedFuture(documentList);

                    return allDocsFuture.thenCompose(list -> {

                        LoggingService.logData("Messages count: " + list.size(), MessageType.INFO);

                        if (list.isEmpty()) {
                            return CompletableFuture.completedFuture(nextCookie);
                        }

                        CompletableFuture<String> chain = CompletableFuture.completedFuture(nextCookie);

                        for (DocumentModel.Document doc : list) {
                            chain = chain.thenCompose(curCookie ->
                                    getFileInsideMessageAsync(
                                            doc.getId(),
                                            doc.getCreatedAt(),
                                            curCookie,
                                            jwtToken,
                                            searchElement,
                                            msg
                                    ).thenApply(updatedCookie ->
                                            (updatedCookie != null && !updatedCookie.isBlank())
                                                    ? updatedCookie
                                                    : curCookie
                                    )
                            );
                        }

                        return chain;
                    });
                })
                .exceptionally(ex -> {
                    LoggingService.logData(ex.getMessage(), MessageType.ERROR);
                    throw new CompletionException(ex);
                });
    }


    private CompletableFuture<List<DocumentModel.Document>> fetchAllDocuments(
            String token,
            String cookie,
            String endpoint,
            MessageType msg,
            String dateStart,
            String dateEnd,
            boolean unread,
            int offset,
            List<DocumentModel.Document> acc
    ) {
        return getExtraDocuments(token, cookie, endpoint, msg, dateStart, dateEnd, unread, offset)
            .thenCompose(extra -> {

                if (extra == null || extra.isEmpty()) {
                    return CompletableFuture.completedFuture(acc);
                }

                Map.Entry<String, DocumentModel.Document[]> entry =
                        extra.entrySet().iterator().next();

                String nextCookie = entry.getKey();
                DocumentModel.Document[] docs = entry.getValue();

                acc.addAll(List.of(docs));

                return fetchAllDocuments(
                    token,
                    nextCookie,
                    endpoint,
                    msg,
                    dateStart,
                    dateEnd,
                    unread,
                    offset + 100,
                    acc
                );
            });
    }


    private CompletableFuture<Map<String, DocumentModel.Document[]>> getExtraDocuments(String token, String cookie,
                                                                           String endpoint, MessageType msg,
                                                                           String dateStart, String dateEnd,
                                                                           boolean unread, int count){

        LoggingService.logData("Request extra documents ...", MessageType.INFO);

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
                "offset": %d,
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
        }""".formatted(count, !unread ? "null" : true, dateStart, dateEnd);


        }else{

            body = """
            {
                "correspondent": {
                    "kind": "taxauthority",
                    "tin": null,
                    "fin": null
                },
                "offset": %d,
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
            }""".formatted(count, !unread ? "null" : true, dateStart, dateEnd);
        }

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(endpoint))
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .timeout(Duration.ofSeconds(15))
            .header("Cookie", cookie)
            .header("x-authorization", "Bearer " + token)
            .header("Content-Type", "application/json")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:142.0) Gecko/20100101 Firefox/142.0")
            .build();

        return client.sendAsync(request, new CustomHttpHandler<>(DocumentModel.class))

            .thenCompose(x -> explicitWait().thenApply(v -> x))
            .thenCompose(response -> {

            DocumentModel dm = response.body();
            String nextCookie = response.headers().firstValue("Set-Cookie")
                    .filter(s -> s.contains("JSESSIONID"))
                    .map(s -> s.split(";")[0])
                    .orElse("");

            if (dm != null && !dm.getMessages().isEmpty()){

                return CompletableFuture.completedFuture(Map.of(nextCookie, dm.getMessages()
                        .toArray(DocumentModel.Document[]::new)));

            }

            return CompletableFuture.completedFuture(null);

        });

    }

    //6
    private CompletableFuture<String> getFileInsideMessageAsync(
            String inboxID, String inboxCreatedAt, String cookieValue, String jwtToken, String searchElement, MessageType msg) {

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/edi/public/v1/message/%s?sourceSystem=avis".formatted(inboxID)))
                .GET()
                .timeout(Duration.ofSeconds(15))
                .header("Cookie", cookieValue)
                .header("x-authorization", "Bearer " + jwtToken)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:142.0) Gecko/20100101 Firefox/142.0")
                .build();

        CompletableFuture<String> promise = new CompletableFuture<>();

        client.sendAsync(request, new CustomHttpHandler<>(MessageDto.class))
            .thenCompose(x -> explicitWait().thenApply(v -> x))
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

                List<CompletableFuture<Void>> fileTasks = new ArrayList<>();
                MessageDto dto = response.body();
                String fileTimestamp = normalizeDate(inboxCreatedAt);

                LoggingService.logData("File timestamp " + fileTimestamp, MessageType.INFO);

                String filePart = dto.getSubject() != null ? dto.getSubject() : dto.getContent();

                LoggingService.logData("File Part " + filePart, MessageType.INFO);

                LoggingService.logData("Data i found inside message", MessageType.INFO);
                LoggingService.logData(dto.toString(), MessageType.INFO);

                if (dto.getFiles() != null && !dto.getFiles().isEmpty()) {

                    for (MessageDto.FileDto file: dto.getFiles()) {
                        String ext = file.getName().substring(file.getName().lastIndexOf(".") + 1);
                        String rawName = (dto.getFiles().size() == 1)
                                ? (filePart + "." + ext)
                                : (filePart + UUID.randomUUID() + "." + ext);

                        String fileName = fileTimestamp + "_" + sanitizeForWindows(rawName);

                        LoggingService.logData("Full fileName is " + fileName, MessageType.INFO);

                        fileTasks.add(downloadAndSaveDocumentAsync(file.getId(), fileName,
                                jwtToken, searchElement, msg));
                    }

                }

                CompletableFuture.allOf(fileTasks.toArray(new CompletableFuture[0]))
                        .whenComplete((v, ex) -> {
                            if (ex == null) promise.complete(newCookie);
                            else promise.completeExceptionally(ex);
                        });
            })
            .exceptionally(ex -> {
                promise.completeExceptionally(ex);
                LoggingService.logData(ex.getMessage(), MessageType.ERROR);
                return null;
            });

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
                LoggingService.logData("Report downloaded successfully to: " + filePath, MessageType.INFO);

                return fileResponse.headers().allValues("Set-Cookie").stream()
                    .filter(s -> s.contains("JSESSIONID"))
                    .map(s -> s.split(";")[0])
                    .findFirst()
                    .orElse(cookieValue);

            })
            .exceptionally(ex -> {
                LoggingService.logData(ex.getMessage(), MessageType.ERROR);
                throw new java.util.concurrent.CompletionException(ex);
            });
    }

    private CompletableFuture<Void> downloadAndSaveDocumentAsync(
            String documentID, String fileName, String jwtToken, String searchElement, MessageType msg) {

        CompletableFuture<Void> promise = new CompletableFuture<>();
        LoggingService.logData("Downloading file with fileName " + fileName, MessageType.INFO);
        LoggingService.logData("Document ID " + documentID, MessageType.INFO);
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
        LoggingService.logData("Path to save a file", MessageType.INFO);
        LoggingService.logData(String.valueOf(filePath), MessageType.INFO);

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
                    LoggingService.logData("File downloaded successfully to: " + filePath, MessageType.INFO);
                    promise.complete(null);
                } else {
                    LoggingService.logData("File download failed: " + fileResponse.statusCode(), MessageType.ERROR);
                }

                promise.complete(null);
            })
            .exceptionally(ex -> {
                promise.completeExceptionally(ex);
                LoggingService.logData(ex.getMessage(), MessageType.ERROR);
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
            LoggingService.logData(e.getMessage(), MessageType.ERROR);
        }
    }

    private void resetProgress() {
        PROCESSED_RECORDS.set(0);
        FAILED_RECORDS_COUNT.set(0);
        COMPLETED_RECORDS_COUNT.set(0);
        TOTAL_RECORDS_COUNT.set(0);
        isCompletionNotified.set(false);
        asanIDs.clear();
    }

    protected void beginRun(int total) {
        resetProgress();
        TOTAL_RECORDS_COUNT.set(total);
    }

}
