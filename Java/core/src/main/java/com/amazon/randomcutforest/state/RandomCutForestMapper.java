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

package com.amazon.randomcutforest.state;

import static com.amazon.randomcutforest.CommonUtils.checkArgument;
import static com.amazon.randomcutforest.CommonUtils.checkNotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.Setter;

import com.amazon.randomcutforest.ComponentList;
import com.amazon.randomcutforest.IComponentModel;
import com.amazon.randomcutforest.RandomCutForest;
import com.amazon.randomcutforest.config.Precision;
import com.amazon.randomcutforest.executor.PassThroughCoordinator;
import com.amazon.randomcutforest.executor.PointStoreCoordinator;
import com.amazon.randomcutforest.executor.SamplerPlusTree;
import com.amazon.randomcutforest.sampler.CompactSampler;
import com.amazon.randomcutforest.sampler.IStreamSampler;
import com.amazon.randomcutforest.sampler.SimpleStreamSampler;
import com.amazon.randomcutforest.sampler.Weighted;
import com.amazon.randomcutforest.state.sampler.ArraySamplersToCompactStateConverter;
import com.amazon.randomcutforest.state.sampler.CompactSamplerMapper;
import com.amazon.randomcutforest.state.sampler.CompactSamplerState;
import com.amazon.randomcutforest.state.store.PointStoreDoubleMapper;
import com.amazon.randomcutforest.state.store.PointStoreFloatMapper;
import com.amazon.randomcutforest.state.store.PointStoreState;
import com.amazon.randomcutforest.state.tree.CompactRandomCutTreeContext;
import com.amazon.randomcutforest.state.tree.CompactRandomCutTreeDoubleMapper;
import com.amazon.randomcutforest.state.tree.CompactRandomCutTreeFloatMapper;
import com.amazon.randomcutforest.state.tree.CompactRandomCutTreeState;
import com.amazon.randomcutforest.store.IPointStore;
import com.amazon.randomcutforest.store.PointStoreDouble;
import com.amazon.randomcutforest.store.PointStoreFloat;
import com.amazon.randomcutforest.tree.CompactRandomCutTreeDouble;
import com.amazon.randomcutforest.tree.CompactRandomCutTreeFloat;
import com.amazon.randomcutforest.tree.ITree;
import com.amazon.randomcutforest.tree.RandomCutTree;

/**
 * A utility class for creating a {@link RandomCutForestState} instance from a
 * {@link RandomCutForest} instance and vice versa.
 */
@Getter
@Setter
public class RandomCutForestMapper
        implements IContextualStateMapper<RandomCutForest, RandomCutForestState, ExecutorContext> {

    /**
     * A flag indicating whether the structure of the trees in the forest should be
     * included in the state object. If true, then data describing the bounding
     * boxes and cuts defining each tree will be written to the
     * {@link RandomCutForestState} object produced by the mapper. Tree state is not
     * saved by default.
     */
    private boolean saveTreeState = false;

    /**
     * A flag indicating whether the point store should be included in the
     * {@link RandomCutForestState} object produced by the mapper. This is saved by
     * default for compact trees
     */
    private boolean saveCoordinatorState = true;

    /**
     * A flag indicating whether the samplers should be included in the
     * {@link RandomCutForestState} object produced by the mapper. This is saved by
     * default for all trees.
     */
    private boolean saveSamplerState = true;

    /**
     * A flag indicating whether the executor context should be included in the
     * {@link RandomCutForestState} object produced by the mapper. Executor context
     * is not saved by defalt.
     */
    private boolean saveExecutorContext = false;

    /**
     * If true, then the arrays are compressed via simple data dependent scheme
     */
    private boolean compress = true;

    /**
     * if true would require that the samplers populate the trees before the trees
     * can be used gain. That woukld correspond to extra time, at the benefit of a
     * smaller serialization.
     */
    private boolean partialTreesInUse = false;

    /**
     * Create a {@link RandomCutForestState} object representing the state of the
     * given forest. If the forest is compact and the {@code saveTreeState} flag is
     * set to true, then structure of the trees in the forest will be included in
     * the state object. If the flag is set to false, then the state object will
     * only contain the sampler data for each tree. If the
     * {@code saveExecutorContext} is true, then the executor context will be
     * included in the state object.
     *
     * @param forest A Random Cut Forest whose state we want to capture.
     * @return a {@link RandomCutForestState} object representing the state of the
     *         given forest.
     * @throws IllegalArgumentException if the {@code saveTreeState} flag is true
     *                                  and the forest is not compact.
     */
    @Override
    public RandomCutForestState toState(RandomCutForest forest) {
        if (saveTreeState) {
            checkArgument(forest.isCompactEnabled(), "tree state cannot be saved for noncompact forests");
        }

        RandomCutForestState state = new RandomCutForestState();

        state.setNumberOfTrees(forest.getNumberOfTrees());
        state.setDimensions(forest.getDimensions());
        state.setLambda(forest.getLambda());
        state.setSampleSize(forest.getSampleSize());
        state.setShingleSize(forest.getShingleSize());
        state.setCenterOfMassEnabled(forest.isCenterOfMassEnabled());
        state.setOutputAfter(forest.getOutputAfter());
        state.setStoreSequenceIndexesEnabled(forest.isStoreSequenceIndexesEnabled());
        state.setTotalUpdates(forest.getTotalUpdates());
        state.setCompactEnabled(forest.isCompactEnabled());
        state.setInternalShinglingEnabled(forest.isInternalShinglingEnabled());
        state.setBoundingBoxCachingEnabled(forest.isBoundingBoxCachingEnabled());
        state.setSaveSamplerState(saveSamplerState);
        state.setSaveTreeState(saveTreeState);
        state.setSaveCoordinatorState(saveCoordinatorState);
        state.setSinglePrecisionSet(forest.getPrecision() == Precision.SINGLE);
        state.setCompress(compress);
        state.setPartialTreesInUse(partialTreesInUse);

        if (saveExecutorContext) {
            ExecutorContext executorContext = new ExecutorContext();
            executorContext.setParallelExecutionEnabled(forest.isParallelExecutionEnabled());
            executorContext.setThreadPoolSize(forest.getThreadPoolSize());
            state.setExecutorContext(executorContext);
        }

        if (forest.isCompactEnabled()) {
            if (saveCoordinatorState) {
                PointStoreCoordinator pointStoreCoordinator = (PointStoreCoordinator) forest.getUpdateCoordinator();
                PointStoreState pointStoreState;
                if (forest.getPrecision() == Precision.SINGLE) {
                    PointStoreFloatMapper mapper = new PointStoreFloatMapper();
                    mapper.setCompress(compress);
                    pointStoreState = mapper.toState((PointStoreFloat) pointStoreCoordinator.getStore());
                } else {
                    PointStoreDoubleMapper mapper = new PointStoreDoubleMapper();
                    mapper.setCompress(compress);
                    pointStoreState = mapper.toState((PointStoreDouble) pointStoreCoordinator.getStore());
                }
                state.setPointStoreState(pointStoreState);
            }
            List<CompactSamplerState> samplerStates = null;
            if (saveSamplerState) {
                samplerStates = new ArrayList<>();
            }
            List<ITree<Integer, ?>> trees = null;
            if (saveTreeState) {
                trees = new ArrayList<>();
            }

            CompactSamplerMapper samplerMapper = new CompactSamplerMapper();
            samplerMapper.setCompress(compress);

            for (IComponentModel<?, ?> component : forest.getComponents()) {
                SamplerPlusTree<Integer, ?> samplerPlusTree = (SamplerPlusTree<Integer, ?>) component;
                CompactSampler sampler = (CompactSampler) samplerPlusTree.getSampler();
                if (samplerStates != null) {
                    samplerStates.add(samplerMapper.toState(sampler));
                }
                if (trees != null) {
                    trees.add(samplerPlusTree.getTree());
                }
            }

            state.setCompactSamplerStates(samplerStates);

            if (trees != null) {
                if (forest.getPrecision() == Precision.SINGLE) {
                    CompactRandomCutTreeFloatMapper treeMapper = new CompactRandomCutTreeFloatMapper();
                    treeMapper.setCompress(compress);
                    treeMapper.setPartialTreeInUse(partialTreesInUse || forest.isStoreSequenceIndexesEnabled());
                    List<CompactRandomCutTreeState> treeStates = trees.stream()
                            .map(t -> treeMapper.toState((CompactRandomCutTreeFloat) t)).collect(Collectors.toList());
                    state.setCompactRandomCutTreeStates(treeStates);
                } else {
                    CompactRandomCutTreeDoubleMapper treeMapper = new CompactRandomCutTreeDoubleMapper();
                    treeMapper.setCompress(compress);
                    treeMapper.setPartialTreeInUse(partialTreesInUse || forest.isStoreSequenceIndexesEnabled());
                    List<CompactRandomCutTreeState> treeStates = trees.stream()
                            .map(t -> treeMapper.toState((CompactRandomCutTreeDouble) t)).collect(Collectors.toList());
                    state.setCompactRandomCutTreeStates(treeStates);
                }
            }
        } else {
            ArraySamplersToCompactStateConverter converter = new ArraySamplersToCompactStateConverter(
                    forest.isStoreSequenceIndexesEnabled(), forest.getDimensions(),
                    forest.getNumberOfTrees() * forest.getSampleSize());

            for (IComponentModel<?, ?> model : forest.getComponents()) {
                SamplerPlusTree<double[], ?> samplerPlusTree = (SamplerPlusTree<double[], ?>) model;
                SimpleStreamSampler<double[]> sampler = (SimpleStreamSampler<double[]>) samplerPlusTree.getSampler();
                converter.addSampler(sampler);
            }

            state.setPointStoreState(converter.getPointStoreDoubleState());
            state.setCompactSamplerStates(converter.getCompactSamplerStates());
        }

        return state;
    }

    /**
     * Create a {@link RandomCutForest} instance from a
     * {@link RandomCutForestState}. If the state contains tree states, then trees
     * will be constructed from the tree state objects. Otherwise, empty trees are
     * created and populated from the sampler data. The resulting forest should be
     * equal in distribution to the forest that the state object was created from.
     *
     * @param state           A Random Cut Forest state object.
     * @param executorContext An executor context that will be used to initialize
     *                        new executors in the Random Cut Forest. If this
     *                        argument is null, then the mapper will look for an
     *                        executor context in the state object.
     * @param seed            A random seed.
     * @return A Random Cut Forest corresponding to the state object.
     * @throws NullPointerException if both the {@code executorContext} method
     *                              argument and the executor context field in the
     *                              state object are null.
     */
    public RandomCutForest toModel(RandomCutForestState state, ExecutorContext executorContext, long seed) {

        ExecutorContext ec;
        if (executorContext != null) {
            ec = executorContext;
        } else {
            checkNotNull(state.getExecutorContext(),
                    "The executor context in the state object is null, an executor context must be passed explicitly to toModel()");
            ec = state.getExecutorContext();
        }

        RandomCutForest.Builder<?> builder = RandomCutForest.builder().numberOfTrees(state.getNumberOfTrees())
                .dimensions(state.getDimensions()).lambda(state.getLambda()).sampleSize(state.getSampleSize())
                .centerOfMassEnabled(state.isCenterOfMassEnabled()).outputAfter(state.getOutputAfter())
                .parallelExecutionEnabled(ec.isParallelExecutionEnabled()).threadPoolSize(ec.getThreadPoolSize())
                .storeSequenceIndexesEnabled(state.isStoreSequenceIndexesEnabled()).shingleSize(state.getShingleSize())
                .boundingBoxCachingEnabled(state.isBoundingBoxCachingEnabled()).compactEnabled(state.isCompactEnabled())
                .internalShinglingEnabled(state.isInternalShinglingEnabled()).randomSeed(seed);

        if (state.isCompactEnabled()) {
            if (state.isSinglePrecisionSet()) {
                return singlePrecisionForest(builder, state, null, null, null);
            } else {
                return doublePrecisionForest(builder, state, null, null, null);
            }
        }

        Random rng = builder.getRandom();
        List<CompactSamplerState> samplerStates = state.getCompactSamplerStates();
        CompactSamplerMapper samplerMapper = new CompactSamplerMapper();

        PointStoreDouble pointStore = new PointStoreDoubleMapper().toModel(state.getPointStoreState());
        PassThroughCoordinator coordinator = new PassThroughCoordinator();
        coordinator.setTotalUpdates(state.getTotalUpdates());
        ComponentList<double[], double[]> components = new ComponentList<>();
        for (int i = 0; i < state.getNumberOfTrees(); i++) {
            CompactSampler compactData = samplerMapper.toModel(samplerStates.get(i));
            RandomCutTree tree = RandomCutTree.builder()
                    .storeSequenceIndexesEnabled(state.isStoreSequenceIndexesEnabled())
                    .centerOfMassEnabled(state.isCenterOfMassEnabled()).randomSeed(rng.nextLong()).build();
            SimpleStreamSampler<double[]> sampler = new SimpleStreamSampler<>(state.getSampleSize(), state.getLambda(),
                    rng.nextLong());
            sampler.setMaxSequenceIndex(compactData.getMaxSequenceIndex());
            sampler.setSequenceIndexOfMostRecentLambdaUpdate(compactData.getSequenceIndexOfMostRecentLambdaUpdate());

            for (Weighted<Integer> sample : compactData.getWeightedSample()) {
                double[] point = pointStore.get(sample.getValue());
                sampler.addSample(new Weighted<>(point, sample.getWeight(), sample.getSequenceIndex()));
                tree.addPoint(point, sample.getSequenceIndex());
            }
            components.add(new SamplerPlusTree<>(sampler, tree));
        }

        return new RandomCutForest(builder, coordinator, components, rng);
    }

    /**
     * Create a {@link RandomCutForest} instance from a {@link RandomCutForestState}
     * using the executor context in the state object. See
     * {@link #toModel(RandomCutForestState, ExecutorContext, long)}.
     *
     * @param state A Random Cut Forest state object.
     * @param seed  A random seed.
     * @return A Random Cut Forest corresponding to the state object.
     * @throws NullPointerException if the executor context field in the state
     *                              object are null.
     */
    public RandomCutForest toModel(RandomCutForestState state, long seed) {
        return toModel(state, null, seed);
    }

    /**
     * Create a {@link RandomCutForest} instance from a {@link RandomCutForestState}
     * using the executor context in the state object. See
     * {@link #toModel(RandomCutForestState, ExecutorContext, long)}.
     *
     * @param state A Random Cut Forest state object.
     * @return A Random Cut Forest corresponding to the state object.
     * @throws NullPointerException if the executor context field in the state
     *                              object are null.
     */
    public RandomCutForest toModel(RandomCutForestState state) {
        return toModel(state, null);
    }

    public RandomCutForest singlePrecisionForest(RandomCutForest.Builder<?> builder, RandomCutForestState state,
            IPointStore<float[]> extPointStore, List<ITree<Integer, float[]>> extTrees,
            List<IStreamSampler<Integer>> extSamplers) {

        checkArgument(builder != null, "builder cannot be null");
        checkArgument(extTrees == null || extTrees.size() == state.getNumberOfTrees(), "incorrect number of trees");
        checkArgument(extSamplers == null || extSamplers.size() == state.getNumberOfTrees(),
                "incorrect number of samplers");
        checkArgument(extSamplers != null | state.isSaveSamplerState(), " need samplers ");
        checkArgument(extPointStore != null || state.isSaveCoordinatorState(), " need coordinator state ");

        Random rng = builder.getRandom();
        ComponentList<Integer, float[]> components = new ComponentList<>();
        CompactRandomCutTreeContext context = new CompactRandomCutTreeContext();
        IPointStore<float[]> pointStore = (extPointStore == null)
                ? new PointStoreFloatMapper().toModel(state.getPointStoreState())
                : extPointStore;
        PointStoreCoordinator<float[]> coordinator = new PointStoreCoordinator<>(pointStore);
        coordinator.setTotalUpdates(state.getTotalUpdates());
        context.setPointStore(pointStore);
        context.setMaxSize(state.getSampleSize());
        CompactRandomCutTreeFloatMapper treeMapper = new CompactRandomCutTreeFloatMapper();
        List<CompactRandomCutTreeState> treeStates = state.isSaveTreeState() ? state.getCompactRandomCutTreeStates()
                : null;
        CompactSamplerMapper samplerMapper = new CompactSamplerMapper();
        List<CompactSamplerState> samplerStates = state.isSaveSamplerState() ? state.getCompactSamplerStates() : null;
        for (int i = 0; i < state.getNumberOfTrees(); i++) {
            IStreamSampler<Integer> sampler = (extSamplers != null) ? extSamplers.get(i)
                    : samplerMapper.toModel(samplerStates.get(i), rng.nextLong());

            ITree<Integer, float[]> tree;
            if (extTrees != null) {
                tree = extTrees.get(i);
            } else if (treeStates != null) {
                tree = treeMapper.toModel(treeStates.get(i), context, rng.nextLong());
                if (treeStates.get(i).isPartialTreeInUse()) {
                    sampler.getSample().forEach(s -> tree.addPoint(s.getValue(), s.getSequenceIndex()));
                }
            } else {
                tree = new CompactRandomCutTreeFloat(state.getSampleSize(), rng.nextLong(), pointStore,
                        state.isBoundingBoxCachingEnabled(), state.isCenterOfMassEnabled(),
                        state.isStoreSequenceIndexesEnabled());
                sampler.getSample().forEach(s -> tree.addPoint(s.getValue(), s.getSequenceIndex()));
            }
            components.add(new SamplerPlusTree<>(sampler, tree));
        }
        builder.precision(Precision.SINGLE);
        return new RandomCutForest(builder, coordinator, components, rng);
    }

    public RandomCutForest doublePrecisionForest(RandomCutForest.Builder<?> builder, RandomCutForestState state,
            IPointStore<double[]> extPointStore, List<ITree<Integer, double[]>> extTrees,
            List<IStreamSampler<Integer>> extSamplers) {

        checkArgument(builder != null, "builder cannot be null");
        checkArgument(extTrees == null || extTrees.size() == state.getNumberOfTrees(), "incorrect number of trees");
        checkArgument(extSamplers == null || extSamplers.size() == state.getNumberOfTrees(),
                "incorrect number of samplers");
        checkArgument(extSamplers != null | state.isSaveSamplerState(), " need samplers ");
        checkArgument(extPointStore != null || state.isSaveCoordinatorState(), " need coordinator state ");

        Random rng = builder.getRandom();
        ComponentList<Integer, double[]> components = new ComponentList<>();
        CompactRandomCutTreeContext context = new CompactRandomCutTreeContext();
        IPointStore<double[]> pointStore = (extPointStore == null)
                ? new PointStoreDoubleMapper().toModel(state.getPointStoreState())
                : extPointStore;
        PointStoreCoordinator<double[]> coordinator = new PointStoreCoordinator<>(pointStore);
        coordinator.setTotalUpdates(state.getTotalUpdates());
        context.setPointStore(pointStore);
        context.setMaxSize(state.getSampleSize());
        CompactRandomCutTreeDoubleMapper treeMapper = new CompactRandomCutTreeDoubleMapper();
        List<CompactRandomCutTreeState> treeStates = state.isSaveTreeState() ? state.getCompactRandomCutTreeStates()
                : null;
        CompactSamplerMapper samplerMapper = new CompactSamplerMapper();
        List<CompactSamplerState> samplerStates = state.isSaveSamplerState() ? state.getCompactSamplerStates() : null;
        for (int i = 0; i < state.getNumberOfTrees(); i++) {

            IStreamSampler<Integer> sampler = (extSamplers != null) ? extSamplers.get(i)
                    : samplerMapper.toModel(samplerStates.get(i), rng.nextLong());

            ITree<Integer, double[]> tree;
            if (extTrees != null) {
                tree = extTrees.get(i);
            } else if (treeStates != null) {
                tree = treeMapper.toModel(treeStates.get(i), context, rng.nextLong());
                if (treeStates.get(i).isPartialTreeInUse()) {
                    sampler.getSample().forEach(s -> tree.addPoint(s.getValue(), s.getSequenceIndex()));
                }
            } else {
                tree = new CompactRandomCutTreeDouble.Builder().maxSize(state.getSampleSize())
                        .randomSeed(rng.nextLong()).pointStore(pointStore)
                        .enableBoundingBoxCaching(state.isBoundingBoxCachingEnabled())
                        .centerOfMassEnabled(state.isCenterOfMassEnabled())
                        .storeSequenceIndexesEnabled(state.isStoreSequenceIndexesEnabled()).build();
                sampler.getSample().forEach(s -> tree.addPoint(s.getValue(), s.getSequenceIndex()));
            }
            components.add(new SamplerPlusTree<>(sampler, tree));
        }
        builder.precision(Precision.DOUBLE);
        return new RandomCutForest(builder, coordinator, components, rng);
    }
}
