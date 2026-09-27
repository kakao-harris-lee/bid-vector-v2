package bidvector.app.wiring

import bidvector.adapters.snapshot.JdbcSnapshotSource
import bidvector.app.collection.CollectionLog
import bidvector.app.collection.CollectionTermination
import bidvector.app.collection.SnapshotExtractionProperties
import bidvector.app.collection.SnapshotExtractionRunner
import bidvector.procurement.CollectionReferenceDate
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import javax.sql.DataSource

/**
 * 스냅숏 추출 배선(M6/6G D-6G-2) — **`bidvector.snapshot-extract.mode=once` 일 때만** 올라온다.
 * DB 를 읽기만 하고 KONEPS 를 부르지 않는다(서비스 키 설정을 요구하지 않는다).
 */
@Configuration
@Import(CollectionTerminationWiring::class)
@ConditionalOnProperty(prefix = "bidvector.snapshot-extract", name = ["mode"], havingValue = "once")
@EnableConfigurationProperties(SnapshotExtractionProperties::class)
open class SnapshotExtractionWiring {
    @Bean
    open fun jdbcSnapshotSource(
        dataSource: DataSource,
        properties: SnapshotExtractionProperties,
    ): JdbcSnapshotSource = JdbcSnapshotSource(dataSource, collectionPolicyAt(CollectionReferenceDate(properties.to)))

    @Bean
    open fun snapshotExtractionRunner(
        source: JdbcSnapshotSource,
        properties: SnapshotExtractionProperties,
        termination: CollectionTermination,
    ): SnapshotExtractionRunner {
        val logger = LoggerFactory.getLogger(SnapshotExtractionRunner::class.java)
        return SnapshotExtractionRunner(source, properties, CollectionLog { logger.info(it) }, termination)
    }
}
