package br.gohan.videofeed.config

import br.gohan.videofeed.db.UserTable
import br.gohan.videofeed.db.VideoTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

object DatabaseConfig {
    fun init(
        url: String = Env.getOrDefault("DB_URL", "jdbc:postgresql://localhost:5432/videofeed"),
        driver: String = "org.postgresql.Driver",
        user: String = Env.getOrDefault("DB_USER", "postgres"),
        password: String = Env.getOrDefault("DB_PASSWORD", "postgres")
    ) {
        Database.connect(url = url, driver = driver, user = user, password = password)
        transaction {
            SchemaUtils.create(UserTable, VideoTable)
        }
    }
}
