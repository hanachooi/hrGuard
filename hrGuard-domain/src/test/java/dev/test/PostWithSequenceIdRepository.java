package dev.test;

import org.springframework.data.jpa.repository.JpaRepository;

interface PostWithSequenceIdRepository extends JpaRepository<saveMethodTest.PostWithSequenceId, Long> {
}
