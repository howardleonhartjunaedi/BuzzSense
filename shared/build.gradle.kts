import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/*
 * BuzzAlert shared module (Kotlin Multiplatform).
 *
 * Holds everything that is not UI: the Koog agent and its strategy graph, the
 * two-stage wingbeat detector wrapper, on-device RAG, guardrails and the provider
 * failover executor. Targets:
 *   - android : runs in the app (Compose UI lives in :app)
 *   - jvm     : runs the same pipeline on a laptop for the eval harness
 */
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "com.buzzsense.veritas.shared"
        compileSdk = 37
        minSdk = 26
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
        withHostTestBuilder {}
    }

    jvm {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    jvmToolchain(17)

    sourceSets {
        commonMain.dependencies {
            // Koog stable core: agents, graph DSL, tools, structured output,
            // Anthropic + OpenAI clients, Ktor-backed HTTP transport.
            api(libs.koog.agents)
            // Koog beta: Gemini client (primary provider in the failover chain).
            implementation(libs.koog.google.client)

            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)

            // On-device guidance store: SQLite with FTS5 (+ sqlite-vec when the
            // extension is bundled), same driver on Android and the JVM.
            implementation(libs.androidx.sqlite)
            implementation(libs.androidx.sqlite.bundled)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.koog.agents.test)
        }
        androidMain {
            // ONNX code (embedder, VAD, wingbeat CNN) is shared with jvmMain by
            // adding the same folder to both source sets: each target compiles it
            // against its own ONNX Runtime artifact (identical ai.onnxruntime API).
            kotlin.srcDir("src/onnxShared/kotlin")
            dependencies {
                // Koog beta: on-device LLM through LiteRT-LM (last tier before the fixed checklist).
                implementation(libs.koog.litert.client)
                implementation(libs.ktor.client.okhttp)
                implementation(libs.onnxruntime.android)
            }
        }
        jvmMain {
            kotlin.srcDir("src/onnxShared/kotlin")
            dependencies {
                implementation(libs.ktor.client.okhttp)
                implementation(libs.onnxruntime.jvm)
            }
        }
    }
}
