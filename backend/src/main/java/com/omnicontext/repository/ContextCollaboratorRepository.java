package com.omnicontext.repository;

import com.omnicontext.model.ContextCollaborator;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ContextCollaboratorRepository extends MongoRepository<ContextCollaborator, String> {
    List<ContextCollaborator> findByContextIdOrderByCreatedAtDesc(String contextId);
    void deleteByContextId(String contextId);
}
