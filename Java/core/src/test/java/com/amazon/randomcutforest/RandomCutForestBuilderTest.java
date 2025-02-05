/*
 * Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.amazon.randomcutforest;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class RandomCutForestBuilderTest {

    private int numberOfTrees;
    private int sampleSize;
    private int outputAfter;
    private int dimensions;
    private double lambda;
    private long randomSeed;
    private int threadPoolSize;
    private RandomCutForest forest;

    public static final int DEFAULT_OUTPUT_AFTER_FRACTION = 4;

    @BeforeEach
    public void setUp() {

        numberOfTrees = 99;
        sampleSize = 201;
        outputAfter = 201 / 5;
        dimensions = 2;
        lambda = 0.12;
        randomSeed = 12345;
        threadPoolSize = 9;

        forest = RandomCutForest.builder().numberOfTrees(numberOfTrees).sampleSize(sampleSize).outputAfter(outputAfter)
                .dimensions(dimensions).timeDecay(lambda).randomSeed(randomSeed).storeSequenceIndexesEnabled(true)
                .centerOfMassEnabled(true).parallelExecutionEnabled(true).threadPoolSize(threadPoolSize).build();
    }

    @Test
    public void testForestBuilderWithCustomArguments() {
        assertEquals(numberOfTrees, forest.getNumberOfTrees());
        assertEquals(sampleSize, forest.getSampleSize());
        assertEquals(outputAfter, forest.getOutputAfter());
        assertEquals(dimensions, forest.getDimensions());
        assertEquals(lambda, forest.getTimeDecay());
        assertTrue(forest.isStoreSequenceIndexesEnabled());
        assertTrue(forest.isCenterOfMassEnabled());
        assertTrue(forest.isParallelExecutionEnabled());
        assertEquals(threadPoolSize, forest.getThreadPoolSize());
    }

    @Test
    public void testDefaultForestWithDimensionArgument() {
        RandomCutForest f = RandomCutForest.defaultForest(10);
        assertEquals(10, f.getDimensions());
        assertEquals(256, f.getSampleSize());
        assertEquals(256 / DEFAULT_OUTPUT_AFTER_FRACTION, f.getOutputAfter());
        assertFalse(f.isStoreSequenceIndexesEnabled());
        assertFalse(f.isCenterOfMassEnabled());
        assertFalse(f.isParallelExecutionEnabled());
        assertEquals(0, f.getThreadPoolSize());
    }

    @Test
    public void testDefaultForestWithDimensionAndRandomSeedArguments() {
        RandomCutForest f = RandomCutForest.defaultForest(11, 123);
        assertEquals(11, f.getDimensions());
        assertEquals(256, f.getSampleSize());
        assertEquals(256 / DEFAULT_OUTPUT_AFTER_FRACTION, f.getOutputAfter());
        assertFalse(f.isStoreSequenceIndexesEnabled());
        assertFalse(f.isCenterOfMassEnabled());
        assertFalse(f.isParallelExecutionEnabled());
        assertEquals(0, f.getThreadPoolSize());
    }

    @Test
    public void testDefaultForestWithCustomOutputAfterArgument() {
        RandomCutForest f = RandomCutForest.defaultForest(10);
        assertEquals(10, f.getDimensions());
        assertEquals(256, f.getSampleSize());
        assertEquals(256 / DEFAULT_OUTPUT_AFTER_FRACTION, f.getOutputAfter());
        assertFalse(f.isStoreSequenceIndexesEnabled());
        assertFalse(f.isCenterOfMassEnabled());
        assertFalse(f.isParallelExecutionEnabled());
        assertEquals(0, f.getThreadPoolSize());
    }

    @Test
    public void testForestBuilderWithDefaultParallelExecutionThreadPoolSize() {
        RandomCutForest forest = RandomCutForest.builder().numberOfTrees(numberOfTrees).sampleSize(sampleSize)
                .outputAfter(outputAfter).dimensions(dimensions).timeDecay(lambda).randomSeed(randomSeed)
                .storeSequenceIndexesEnabled(true).centerOfMassEnabled(true).parallelExecutionEnabled(true).build();
        assertEquals(numberOfTrees, forest.getNumberOfTrees());
        assertEquals(sampleSize, forest.getSampleSize());
        assertEquals(outputAfter, forest.getOutputAfter());
        assertEquals(dimensions, forest.getDimensions());
        assertEquals(lambda, forest.getTimeDecay());
        assertTrue(forest.isStoreSequenceIndexesEnabled());
        assertTrue(forest.isCenterOfMassEnabled());
        assertTrue(forest.isParallelExecutionEnabled());
        assertEquals(Runtime.getRuntime().availableProcessors() - 1, forest.getThreadPoolSize());
    }

    @Test
    public void testForestBuilderWithDefaultLambdaValue() {
        RandomCutForest forest = RandomCutForest.builder().dimensions(4).sampleSize(sampleSize).build();
        assertEquals(1.0 / (RandomCutForest.DEFAULT_SAMPLE_SIZE_COEFFICIENT_IN_TIME_DECAY * sampleSize),
                forest.getTimeDecay());
    }

    @Test
    public void testIllegalExceptionIsThrownWhenNumberOfTreesIsZero() {
        assertThrows(IllegalArgumentException.class, () -> RandomCutForest.builder().numberOfTrees(0)
                .sampleSize(sampleSize).dimensions(dimensions).timeDecay(lambda).build());
    }

    @Test
    public void testIllegalExceptionIsThrownWhenSampleSizeIsZero() {
        assertThrows(IllegalArgumentException.class, () -> RandomCutForest.builder().numberOfTrees(numberOfTrees)
                .sampleSize(0).dimensions(dimensions).timeDecay(lambda).build());
    }

    @Test
    public void testIllegalExceptionIsThrownWhenOutputAfterIsNegative() {
        assertThrows(IllegalArgumentException.class, () -> RandomCutForest.builder().numberOfTrees(numberOfTrees)
                .sampleSize(sampleSize).outputAfter(-10).dimensions(dimensions).timeDecay(lambda).build());
    }

    @Test
    public void testIllegalExceptionIsNotThrownWhenOutputAfterIsGreaterThanSample() {
        assertDoesNotThrow(() -> RandomCutForest.builder().numberOfTrees(numberOfTrees).sampleSize(sampleSize)
                .outputAfter(sampleSize + 1).dimensions(dimensions).timeDecay(lambda).build());
    }

    @Test
    public void testIllegalExceptionIsThrownWhenDimensionIsNotProvided() {
        assertThrows(IllegalArgumentException.class, () -> RandomCutForest.builder().numberOfTrees(numberOfTrees)
                .sampleSize(sampleSize).timeDecay(lambda).build());
    }

    @Test
    public void testIllegalExceptionIsThrownWhenLambdaIsNegative() {
        assertThrows(IllegalArgumentException.class, () -> RandomCutForest.builder().numberOfTrees(numberOfTrees)
                .sampleSize(sampleSize).dimensions(dimensions).timeDecay(-0.1).build());
    }

    @Test
    public void testIllegalExceptionIsThrownWhenPoolSizeIsZero() {
        assertThrows(IllegalArgumentException.class,
                () -> RandomCutForest.builder().numberOfTrees(numberOfTrees).sampleSize(sampleSize)
                        .dimensions(dimensions).threadPoolSize(0).parallelExecutionEnabled(true).build());
    }

    @Test
    public void testPoolSizeIsZeroWhenParallelExecutionIsDisabled() {
        RandomCutForest f = RandomCutForest.builder().numberOfTrees(numberOfTrees).sampleSize(sampleSize)
                .dimensions(dimensions).parallelExecutionEnabled(false).build();

        assertFalse(f.isParallelExecutionEnabled());
        assertEquals(0, f.getThreadPoolSize());
    }
}
