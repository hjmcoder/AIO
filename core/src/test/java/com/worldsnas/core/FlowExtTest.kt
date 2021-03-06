package com.worldsnas.core

import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runBlockingTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class FlowExtTest{

    @Test
    fun `returns if flow was empty`() = runBlockingTest {
        val result = flowOf(1,2,3)
            .drop(3)
            .ifEmptyEmit(4)
            .toList()

        assertThat(result).hasSize(1)
            .isEqualTo(listOf(4))
    }

    @Test
    fun `returns null if flow was empty`() = runBlockingTest {
        val result = flowOf(1,2,3)
            .drop(3)
            .ifEmptyEmit(null)
            .toList()

        assertThat(result)
            .hasSize(1)
            .isEqualTo(listOf<Int?>(null))
    }

    @Test
    fun `does not emit the empty item`() = runBlockingTest {
        val result = flowOf(1,2,3)
            .drop(1)
            .ifEmptyEmit(4)
            .toList()

        assertThat(result)
            .hasSize(2)
            .isEqualTo(listOf(2,3))
    }

    @Test
    fun `lists all items`() = runBlockingTest {
        val value = flowOf(1,2,3)
            .toListFlow()
            .first()

        assertThat(value)
            .hasSize(3)
            .isEqualTo(listOf(1,2,3))
    }

    @Test
    fun `emits empty list`() = runBlockingTest {
        val value = flowOf<Int>()
            .toListFlow()
            .first()

        assertThat(value)
            .hasSize(0)
            .isEqualTo(listOf<Int>())
    }

}