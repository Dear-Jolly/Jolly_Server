package com.dearjolly.server.global.storage;

import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import java.io.InputStream;
import java.util.OptionalLong;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ObjectStorage {
    private static final String NO_SUCH_KEY = "NoSuchKey";

    private final MinioClient minioClient;
    private final MinioProperties minioProperties;

    public boolean bucketExists() {
        try {
            return minioClient.bucketExists(BucketExistsArgs.builder()
                    .bucket(minioProperties.bucket())
                    .build());
        } catch (Exception e) {
            throw new ObjectStorageException("버킷 존재 확인에 실패했습니다: " + minioProperties.bucket(), e);
        }
    }

    // 없으면 empty. 크기까지 돌려주므로 호출부가 "있지만 내용이 다른" 경우를 구분할 수 있다.
    public OptionalLong sizeOf(String key) {
        try {
            StatObjectResponse stat = minioClient.statObject(StatObjectArgs.builder()
                    .bucket(minioProperties.bucket())
                    .object(key)
                    .build());
            return OptionalLong.of(stat.size());
        } catch (ErrorResponseException e) {
            if (NO_SUCH_KEY.equals(e.errorResponse().code())) {
                return OptionalLong.empty();
            }
            throw new ObjectStorageException("오브젝트 조회에 실패했습니다: " + key, e);
        } catch (Exception e) {
            throw new ObjectStorageException("오브젝트 조회에 실패했습니다: " + key, e);
        }
    }

    public void upload(String key, InputStream content, long size, String contentType) {
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(minioProperties.bucket())
                    .object(key)
                    .stream(content, size, -1)
                    .contentType(contentType)
                    .build());
        } catch (Exception e) {
            throw new ObjectStorageException("오브젝트 업로드에 실패했습니다: " + key, e);
        }
    }
}
