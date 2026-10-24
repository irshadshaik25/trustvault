
package com.irshad.trustvault.repository;

import com.irshad.trustvault.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentRepository
        extends JpaRepository<Document, Long> {

    List<Document> findByOwner_Id(Long ownerId);
    List<Document> findByOwner_Email(String email);
}
