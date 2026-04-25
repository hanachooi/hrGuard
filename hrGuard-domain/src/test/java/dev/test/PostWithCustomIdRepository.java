package dev.test;

import org.springframework.data.jpa.repository.JpaRepository;

interface PostWithCustomIdRepository extends JpaRepository<saveMethodTest.PostWithCustomId, String> {
}
