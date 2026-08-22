package com.dearjolly.server.global.version.repository;

import com.dearjolly.server.global.version.entity.AppVersions;
import com.dearjolly.server.global.version.enums.Platform;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppVersionRepository extends JpaRepository<AppVersions, Platform> {
}
