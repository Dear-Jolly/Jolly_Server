package com.dearjolly.server.domain.skeleton.dto.request;

import com.dearjolly.server.domain.skeleton.entity.Skeleton;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.Length;

public record SkeletonCreateRequest(

        @NotBlank(message = "카테고리 이름은 필수입니다.")
        @Length(max = 100, message = "카테고리 이름은 100자를 넘을 수 없습니다.")
        String categoryName,

        @NotBlank(message = "카테고리 이미지는 필수입니다.")
        String categoryImage
) {
        public Skeleton toEntity() {
                return Skeleton.create(categoryName, categoryImage);
        }
}
