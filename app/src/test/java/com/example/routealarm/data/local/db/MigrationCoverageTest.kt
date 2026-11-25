package com.example.routealarm.data.local.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 내보낸 스키마(app/schemas)의 모든 버전 사이에 Migration 이 있는지 확인한다.
 * 파괴적 마이그레이션을 쓰지 않으므로 하나라도 빠지면 업데이트한 사용자의 앱이 열리지 않는다.
 */
class MigrationCoverageTest {

    private val schemaVersions: List<Int> =
        File("schemas/${RouteAlarmDatabase::class.qualifiedName}")
            .listFiles { file -> file.extension == "json" }
            .orEmpty()
            .mapNotNull { it.nameWithoutExtension.toIntOrNull() }
            .sorted()

    @Test
    fun `스키마 버전은 1부터 빠짐없이 이어진다`() {
        assertTrue("app/schemas 에 스키마가 없다", schemaVersions.isNotEmpty())
        assertEquals((1..schemaVersions.last()).toList(), schemaVersions)
    }

    @Test
    fun `이웃한 모든 버전 사이에 Migration 이 있다`() {
        val covered = Migrations.ALL.map { it.startVersion to it.endVersion }.toSet()
        schemaVersions.zipWithNext().forEach { step ->
            assertTrue("${step.first} → ${step.second} Migration 이 Migrations.ALL 에 없다", step in covered)
        }
    }
}
