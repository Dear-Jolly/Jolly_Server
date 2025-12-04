package com.dearjolly.server.domain.skeleton.dto.response;

import com.dearjolly.server.domain.skeleton.entity.Skeleton;


public record SkeletonGetResponse(
        String categoryName,
        String categoryImage
) {
    public static SkeletonGetResponse of(Skeleton skeleton) {
        return new SkeletonGetResponse(
                skeleton.getSkeletonCategoryName(),
                skeleton.getSkeletonCategoryImage()
        );
    }
}
