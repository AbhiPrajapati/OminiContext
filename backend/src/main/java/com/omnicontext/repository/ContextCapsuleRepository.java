package com.omnicontext.repository;

import com.omnicontext.model.ContextCapsule;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ContextCapsuleRepository extends MongoRepository<ContextCapsule, String> {

    Optional<ContextCapsule> findByShareSlug(String shareSlug);

    List<ContextCapsule> findByProjectOrderByUpdatedAtDesc(String project);

    @Query(value = "{ '$or': [ " +
                   "{ 'title': { '$regex': ?0, '$options': 'i' } }, " +
                   "{ 'project': { '$regex': ?0, '$options': 'i' } }, " +
                   "{ 'tags': { '$regex': ?0, '$options': 'i' } } " +
                   "] }", sort = "{ 'updatedAt': -1 }")
    List<ContextCapsule> searchByKeyword(String query);
}
