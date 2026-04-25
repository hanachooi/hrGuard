package dev.test;

import org.springframework.data.jpa.repository.JpaRepository;

interface PostRepository extends JpaRepository<saveMethodTest.Post, Long> {
}
