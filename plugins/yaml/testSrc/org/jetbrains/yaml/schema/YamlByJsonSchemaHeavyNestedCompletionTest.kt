// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.schema

import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.impl.ExtensionPointImpl
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiFile
import com.jetbrains.jsonSchema.extension.JsonSchemaMapper
import com.jetbrains.jsonSchema.extension.JsonSchemaMapper.SchemaAndAdditionalCachingDependencies
import com.jetbrains.jsonSchema.extension.schemaWithoutAdditionalCachingDependencies
import com.jetbrains.jsonSchema.impl.JsonBySchemaHeavyCompletionTestBase
import com.jetbrains.jsonSchema.impl.JsonSchemaObject
import com.jetbrains.jsonSchema.impl.assertThatSchema
import com.jetbrains.jsonSchema.impl.nestedCompletions.buildNestedCompletionsRootTree
import com.jetbrains.jsonSchema.impl.withConfiguration
import org.intellij.lang.annotations.Language
import java.io.File

class YamlByJsonSchemaHeavyNestedCompletionTest: JsonBySchemaHeavyCompletionTestBase() {
  fun `test nested completion into property that does not exist yet`() {
    open1ThenOpen2Then3Schema
      .appliedToYamlFile("""
        one:
          thr<caret>
      """.trimIndent())
      .completesTo("""
        one:
          two:
            three: <selection>false</selection>
      """.trimIndent())
  }

  fun `test nested completion from empty file`() {
    open1ThenOpen2Then3Schema
      .appliedToYamlFile("""
        thr<caret>
      """.trimIndent())
      .completesTo("""
        one:
          two:
            three: <selection>false</selection>
      """.trimIndent())
  }

  fun `test nested completion into existing property`() {
    open1ThenOpen2Then3Schema
      .appliedToYamlFile("""
        one:
          thr<caret>
          two:
            foo: bar
      """.trimIndent())
      .completesTo("""
        one:
          two:
            three: <selection>false</selection>
            foo: bar
      """.trimIndent())
  }

  fun `test nested completion into property already exists with sub-property that does not exist yet`() {
    open1ThenOpen2Then3Schema
      .appliedToYamlFile("""
        thr<caret>
        one:
          foo: bar
      """.trimIndent())
      .completesTo("""
        one:
          two:
            three: <selection>false</selection>
          foo: bar
      """.trimIndent())
  }

  fun `test array item completion into existing property`() {
    assertThatSchema("""
      {
        "properties": {
          "foo": {
            "properties": {
              "arr": {
                "type": "array"
              }
            }
          }
        }
      }
    """.trimIndent())
      .withConfiguration {
        buildNestedCompletionsRootTree {
          open("foo")
        }
      }
      .appliedToYamlFile("""
        arr<caret>
        foo:
          bar: baz
      """.trimIndent())
      .completesTo("""
        foo:
          arr:
            - <caret>
          bar: baz
      """.trimIndent())
  }

  fun `test nested array item completion into existing property`() {
    assertThatSchema("""
      {
        "properties": {
          "foo": {
            "properties": {
              "bar": {
                "properties": {
                  "arr": {
                    "type": "array"
                  }
                }
              }
            }
          }
        }
      }
    """.trimIndent())
      .withConfiguration {
        buildNestedCompletionsRootTree {
          open("foo") {
            open("bar")
          }
        }
      }
      .appliedToYamlFile("""
        arr<caret>
        foo:
          baz: 42
      """.trimIndent())
      .completesTo("""
        foo:
          bar:
            arr:
              - <caret>
          baz: 42
      """.trimIndent())
  }

  fun `test array item completion into existing property while inside a property`() {
    assertThatSchema("""
      {
        "properties": {
          "nested": {
            "properties": {
              "foo": {
                "properties": {
                  "arr": {
                    "type": "array"
                  }
                }
              }
            }
          }
        }
      }
    """.trimIndent())
      .withConfiguration {
        buildNestedCompletionsRootTree {
          open("nested") {
            open("foo")
          }
        }
      }
      .appliedToYamlFile("""
        nested:
          arr<caret>
          foo:
            bar: baz
      """.trimIndent())
      .completesTo("""
        nested:
          foo:
            arr:
              - <caret>
            bar: baz
      """.trimIndent())
  }

  fun `test that completing while being below the destination, inserts the completion at the bottom`() {
    open1ThenOpen2Then3Schema
      .appliedToYamlFile("""
        one:
          two:
            foo: false
        thr<caret>
      """.trimIndent())
      .completesTo("""
        one:
          two:
            foo: false
            three: <selection>false</selection>
      """.trimIndent())

    open1ThenOpen2Then3Schema
      .appliedToYamlFile("""
        one:
          two:
            foo: false
          twosBrother: 4
        onesBrother: 2
        thr<caret>
      """.trimIndent())
      .completesTo("""
        one:
          two:
            foo: false
            three: <selection>false</selection>
          twosBrother: 4
        onesBrother: 2
      """.trimIndent())

    open1ThenOpen2Then3Schema
      .appliedToYamlFile("""
        one:
          two:
            foo: false
          twosBrother: 4
          thr<caret>
        onesBrother: 2
      """.trimIndent())
      .completesTo("""
        one:
          two:
            foo: false
            three: <selection>false</selection>
          twosBrother: 4
        onesBrother: 2
      """.trimIndent())

    open1ThenOpen2Then3Schema
      .appliedToYamlFile("""
        one:
          twosBrother: 4
        onesBrother: 2
        thr<caret>
      """.trimIndent())
      .completesTo("""
        one:
          twosBrother: 4
          two:
            three: <selection>false</selection>
        onesBrother: 2
      """.trimIndent())
  }

  private fun JsonSchemaYamlSetup.completesTo(@Language("YAML") expectedResult: String) {
    workingFolder.resolve("Schema.json").createTemporarilyWithContent(schemaSetup.schemaJson) {
      workingFolder.resolve("test.yml").createTemporarilyWithContent(yaml) {
        workingFolder.resolve("test_after.yml").createTemporarilyWithContent(expectedResult) {
          JsonSchemaMapper.EXTENSION_POINT_NAME.maskingExtensions(listOf(schemaSetup.configurator.asSchemaMapper())) {
            baseInsertTest(".", "test")
          }
        }
      }
    }
  }

  override fun getTestDataPath(): String = PathManagerEx.getCommunityHomePath() + "/plugins/yaml/testSrc/org/jetbrains/yaml/schema/data/completion/temp"
  override fun getBasePath(): String = throw IllegalStateException("Use getTestDataPath instead")

  private val workingFolder get() = File(testDataPath)

  override fun setUp() {
    super.setUp()
    workingFolder.mkdirs()
  }

  override fun getExtensionWithoutDot(): String = "yml"

  override fun tearDown() {
    try {
      workingFolder.delete()
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
  }

}


private fun <T : Any> ExtensionPointName<T>.maskingExtensions(extensions: List<T>, block: () -> Unit) {
  val disposable = Disposer.newDisposable()
  try {
    (point as ExtensionPointImpl).maskAll(extensions, disposable, false)
    block()
  } finally {
    Disposer.dispose(disposable)
  }
}

private inline fun File.createTemporarilyWithContent(content: String, block: () -> Unit) {
  try {
    writeText(content)
    block()
  } finally {
    delete()
  }
}

private fun (JsonSchemaObject.() -> Unit).asSchemaMapper(): JsonSchemaMapper = object : JsonSchemaMapper {
  override fun map(file: PsiFile, schemaObject: JsonSchemaObject): SchemaAndAdditionalCachingDependencies =
    schemaWithoutAdditionalCachingDependencies(schemaObject.apply(this@asSchemaMapper))
}

internal val open1ThenOpen2Then3Schema get() = assertThatSchema("""
     {
       "properties": {
         "one": {
           "properties": {
             "two": {
               "properties": {
                 "three": {
                   "type": "boolean"
                 }
               }
             }
           }
         }
       }
     }
   """.trimIndent())
  .withConfiguration {
    buildNestedCompletionsRootTree {
      open("one") {
        open("two")
      }
    }
  }
