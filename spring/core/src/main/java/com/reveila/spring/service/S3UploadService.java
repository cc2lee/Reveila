package com.reveila.spring.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import java.time.Duration;

@Service // Tells Spring to manage this class and allow Dependency Injection
public class S3UploadService {

    private final S3Presigner presigner; // Injected via constructor

    public S3UploadService(S3Presigner presigner) {
        this.presigner = presigner;
    }

    /**
     * Generates a pre-signed URL only if the logged-in user 
     * matches the folderId they are trying to upload to.
     */
    @PreAuthorize("hasRole('USER') and #folderId == principal.s3FolderId")
    public String generatePresignedUploadUrl(String folderId, String fileName) {
        
        // This is the actual logic that replaces the "https://..." placeholder
        PutObjectRequest objectRequest = PutObjectRequest.builder()
                .bucket("my-app-bucket")
                .key(folderId + "/" + fileName) // Organizes files by folderId
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(15))
                .putObjectRequest(objectRequest)
                .build();

        // The S3Presigner generates the real, cryptographically signed URL
        PresignedPutObjectRequest presignedRequest = presigner.presignPutObject(presignRequest);
        
        return presignedRequest.url().toString(); 
    }
}
