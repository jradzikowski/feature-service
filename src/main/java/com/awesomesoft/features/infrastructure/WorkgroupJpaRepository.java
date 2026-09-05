package com.awesomesoft.features.infrastructure;

import com.awesomesoft.features.domain.Workgroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface WorkgroupJpaRepository extends JpaRepository<Workgroup, UUID> {

    boolean existsByName(String name);

    @Query("select w from Workgroup w where lower(w.name) like lower(concat('%', :q, '%')) order by w.name")
    List<Workgroup> searchByName(@Param("q") String q);

    List<Workgroup> findAllByOrderByNameAsc();
}
