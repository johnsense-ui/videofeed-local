package br.gohan.videofeed

import br.gohan.videofeed.config.DatabaseConfig

object TestDatabaseConfig {
    fun init() = DatabaseConfig.init(
        url = "jdbc:h2:mem:test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        driver = "org.h2.Driver",
        user = "sa",
        password = ""
    )
}
