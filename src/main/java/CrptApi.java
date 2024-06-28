import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class CrptApi {

    private final Semaphore semaphore;
    private final int requestLimit;
    private final long timeIntervalMillis;
    private long lastRequestTime;
    private final AtomicInteger requestCount;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.requestLimit = requestLimit;
        this.timeIntervalMillis = timeUnit.toMillis(1);
        this.semaphore = new Semaphore(requestLimit);
        this.lastRequestTime = System.currentTimeMillis();
        this.requestCount = new AtomicInteger(0);
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
    }

    public synchronized void createDocument(Document document, String signature) throws InterruptedException {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastRequestTime >= timeIntervalMillis) {
            requestCount.set(0);
            lastRequestTime = currentTime;
        }

        while (requestCount.get() >= requestLimit) {
            long sleepTime = timeIntervalMillis - (currentTime - lastRequestTime);
            if (sleepTime > 0) {
                Thread.sleep(sleepTime);
            }
            currentTime = System.currentTimeMillis();
            if (currentTime - lastRequestTime >= timeIntervalMillis) {
                requestCount.set(0);
                lastRequestTime = currentTime;
            }
        }

        semaphore.acquire();
        try {
            requestCount.incrementAndGet();
            // Выполнить запрос к API
            sendRequestToApi(document, signature);
        } finally {
            semaphore.release();
        }
    }

    private void sendRequestToApi(Document document, String signature) {
        try {
            String url = "https://ismp.crpt.ru/api/v3/lk/documents/create";
            // Создаем JSON из объекта документа и подписи
            DocumentRequest requestPayload = new DocumentRequest(document, signature);
            String json = objectMapper.writeValueAsString(requestPayload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            System.out.println("Response status code: " + response.statusCode());
            System.out.println("Response body: " + response.body());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Data
    public static class DocumentRequest {
        private final Document document;
        private final String signature;

        public DocumentRequest(Document document, String signature) {
            this.document = document;
            this.signature = signature;
        }

        public Document getDocument() {
            return document;
        }

        public String getSignature() {
            return signature;
        }
    }

    @Data
    public static class Document {
        @JsonProperty("description")
        private Description description;

        public static class Description {
            @JsonProperty("participantInn")
            private String participantInn;

            @JsonProperty("doc_id")
            private String docId;

            @JsonProperty("doc_status")
            private String docStatus;

            @JsonProperty("doc_type")
            private String docType;

            @JsonProperty("importRequest")
            private boolean importRequest;

            @JsonProperty("owner_inn")
            private String ownerInn;

            @JsonProperty("participant_inn")
            private String participant_inn;

            @JsonProperty("producer_inn")
            private String producerInn;

            @JsonProperty("production_date")
            private String productionDate;

            @JsonProperty("production_type")
            private String productionType;

            @JsonProperty("products")
            private List<Product> products;

            @JsonProperty("reg_date")
            private String regDate;

            @JsonProperty("reg_number")
            private String regNumber;
        }

        @Data
        public static class Product {
            @JsonProperty("certificate_document")
            private String certificateDocument;

            @JsonProperty("certificate_document_date")
            private String certificateDocumentDate;

            @JsonProperty("certificate_document_number")
            private String certificateDocumentNumber;

            @JsonProperty("owner_inn")
            private String ownerInn;

            @JsonProperty("producer_inn")
            private String producerInn;

            @JsonProperty("production_date")
            private String productionDate;

            @JsonProperty("tnved_code")
            private String tnvedCode;

            @JsonProperty("uit_code")
            private String uitCode;

            @JsonProperty("uitu_code")
            private String uituCode;
        }
    }
}
