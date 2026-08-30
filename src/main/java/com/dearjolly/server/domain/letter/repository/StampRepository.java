package com.dearjolly.server.domain.letter.repository;

import com.dearjolly.server.domain.letter.entity.Stamps;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StampRepository extends JpaRepository<Stamps, Long> {
    Optional<Stamps> findByName(String name);

    List<Stamps> findAllByNameNotIn(List<String> names);
}
