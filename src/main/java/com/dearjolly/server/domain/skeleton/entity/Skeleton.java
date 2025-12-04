package com.dearjolly.server.domain.skeleton.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "SKELETONS")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Skeleton {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "skeleton_id")
    private Integer id;

    @Column(name = "skeleton_category_name", nullable = false, length = 100)
    private String skeletonCategoryName;

    @Column(name = "skeleton_category_image", nullable = false, columnDefinition = "TEXT")
    private String skeletonCategoryImage;

    private Skeleton(String skeletonCategoryName, String skeletonCategoryImage) {
        this.skeletonCategoryName = skeletonCategoryName;
        this.skeletonCategoryImage = skeletonCategoryImage;
    }

    // ========= 생성 메서드 =========
    public static Skeleton create(String skeletonCategoryName, String skeletonCategoryImage) {
        return new Skeleton(skeletonCategoryName, skeletonCategoryImage);
    }

    // ========= 비즈니스 로직 메서드 =========
    public void updateSkeleton(String skeletonCategoryName, String skeletonCategoryImage) {
        this.skeletonCategoryName = skeletonCategoryName;
        this.skeletonCategoryImage = skeletonCategoryImage;
    }
}
