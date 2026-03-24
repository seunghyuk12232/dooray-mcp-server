package com.bifos.dooray.mcp.util

import java.io.File

fun parseEnv(): Map<String, String> {
    val env = System.getenv().toMutableMap()

    val envFile = File(".env")
    if (envFile.exists()) {
        println("📄 .env 파일에서 환경변수를 로드합니다...")

        envFile.readLines().forEach { line ->
            val trimmedLine = line.trim()
            if (trimmedLine.isNotEmpty() && !trimmedLine.startsWith("#")) {
                val parts = trimmedLine.split("=", limit = 2)
                if (parts.size == 2) {
                    val key = parts[0].trim()
                    val value = parts[1].trim().removeSurrounding("\"").removeSurrounding("'")
                    env[key] = value
                    println("  ✅ $key = ${value.take(10)}...")
                }
            }
        }
    } else {
        println("ℹ️ .env 파일이 없어 시스템 환경변수만 사용합니다.")
    }

    return env
}
