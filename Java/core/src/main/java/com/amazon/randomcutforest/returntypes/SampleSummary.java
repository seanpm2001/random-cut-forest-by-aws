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

package com.amazon.randomcutforest.returntypes;

import static com.amazon.randomcutforest.CommonUtils.checkArgument;
import static com.amazon.randomcutforest.CommonUtils.toDoubleArray;
import static com.amazon.randomcutforest.CommonUtils.toFloatArray;
import static com.amazon.randomcutforest.util.Weighted.prefixPick;
import static java.lang.Math.max;
import static java.util.stream.Collectors.toCollection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.amazon.randomcutforest.util.Weighted;

public class SampleSummary {

    public static double DEFAULT_PERCENTILE = 0.9;

    /**
     * a collection of summarized points (reminiscent of typical sets from the
     * perspective of information theory, Cover and Thomas, Chapter 3) which should
     * be the mean/median of a spatially continuous distribution with central
     * tendency. If the input is a collection of samples that correspond to an union
     * of two such well separated distributions, for example as in the example data
     * of RCF paper then the output should be the two corresponding central points.
     */
    public float[][] summaryPoints;

    /**
     * a measure of comparison among the typical points;
     */
    public float[] relativeWeight;

    /**
     * number of samples, often the number of summary
     */
    public double weightOfSamples;
    /**
     * the global mean
     */
    public float[] mean;

    public float[] median;

    /**
     * This is the global deviation, without any filtering on the TreeSamples
     */
    public float[] deviation;

    /**
     * an upper percentile corresponding to the points, computed dimension
     * agnostically
     */
    public float[] upper;

    /**
     * a lower percentile corresponding to the points
     */
    public float[] lower;

    public SampleSummary(int dimensions) {
        this.weightOfSamples = 0;
        this.summaryPoints = new float[1][];
        this.summaryPoints[0] = new float[dimensions];
        this.relativeWeight = new float[] { 0.0f };
        this.median = new float[dimensions];
        this.mean = new float[dimensions];
        this.deviation = new float[dimensions];
        this.upper = new float[dimensions];
        this.lower = new float[dimensions];
    }

    // for older tests
    public SampleSummary(float[] point) {
        this(toDoubleArray(point), 1.0f);
    }

    public SampleSummary(double[] point, float weight) {
        this(point.length);
        this.weightOfSamples = weight;
        this.summaryPoints[0] = toFloatArray(point);
        this.relativeWeight[0] = weight;
        System.arraycopy(this.summaryPoints[0], 0, this.median, 0, point.length);
        System.arraycopy(this.summaryPoints[0], 0, this.mean, 0, point.length);
        System.arraycopy(this.summaryPoints[0], 0, this.upper, 0, point.length);
        System.arraycopy(this.summaryPoints[0], 0, this.lower, 0, point.length);
    }

    void addTypical(float[][] summaryPoints, float[] relativeWeight) {
        checkArgument(summaryPoints.length == relativeWeight.length, "incorrect lengths of fields");
        if (summaryPoints.length > 0) {
            int dimension = summaryPoints[0].length;
            this.summaryPoints = new float[summaryPoints.length][];
            for (int i = 0; i < summaryPoints.length; i++) {
                checkArgument(dimension == summaryPoints[i].length, " incorrect length points");
                this.summaryPoints[i] = Arrays.copyOf(summaryPoints[i], dimension);
            }
            this.relativeWeight = Arrays.copyOf(relativeWeight, relativeWeight.length);
        }
    }

    public SampleSummary(List<Weighted<float[]>> points, float[][] summaryPoints, float[] relativeWeight,
            double percentile) {
        this(points, percentile);
        this.addTypical(summaryPoints, relativeWeight);
    }

    public SampleSummary(List<Weighted<float[]>> points, float[][] summaryPoints, float[] relativeWeight) {
        this(points, summaryPoints, relativeWeight, DEFAULT_PERCENTILE);
    }

    public SampleSummary(List<Weighted<float[]>> points) {
        this(points, DEFAULT_PERCENTILE);
    }

    /**
     * constructs a summary of the weighted points based on the percentile envelopes
     * by picking 1-precentile and percentile fractional rank of the items useful in
     * surfacing a robust range of values
     * 
     * @param points     weighted points
     * @param percentile value corresponding to bounds
     */
    public SampleSummary(List<Weighted<float[]>> points, double percentile) {
        checkArgument(points.size() > 0, "point list cannot be empty");
        checkArgument(percentile > 0.5, " has to be more than 0.5");
        checkArgument(percentile < 1.0, "has to be less than 1");
        int dimension = points.get(0).index.length;
        double[] coordinateSum = new double[dimension];
        double[] coordinateSumSquare = new double[dimension];
        double totalWeight = 0;
        for (Weighted<float[]> e : points) {
            checkArgument(e.index.length == dimension, "points have to be of same length");
            float weight = e.weight;
            checkArgument(!Float.isNaN(weight), " weights must be non-NaN values ");
            checkArgument(Float.isFinite(weight), " weights must be finite ");
            checkArgument(weight >= 0, "weights have to be non-negative");
            totalWeight += weight;
            for (int i = 0; i < dimension; i++) {
                int index = i;
                checkArgument(!Float.isNaN(e.index[i]),
                        () -> " improper input, in coordinate " + index + ", must be non-NaN values");
                checkArgument(Float.isFinite(e.index[i]),
                        () -> " improper input, in coordinate " + index + ", must be finite values");
                coordinateSum[i] += e.index[i] * weight;
                coordinateSumSquare[i] += e.index[i] * e.index[i] * weight;
            }
        }
        checkArgument(totalWeight > 0, " weights cannot all be 0");
        this.weightOfSamples = totalWeight;
        this.mean = new float[dimension];
        this.deviation = new float[dimension];
        this.median = new float[dimension];
        this.upper = new float[dimension];
        this.lower = new float[dimension];

        for (int i = 0; i < dimension; i++) {
            this.mean[i] = (float) (coordinateSum[i] / totalWeight);
            this.deviation[i] = (float) Math.sqrt(max(0.0, coordinateSumSquare[i] / totalWeight - mean[i] * mean[i]));
        }
        for (int i = 0; i < dimension; i++) {
            int index = i;
            ArrayList<Weighted<Float>> list = points.stream().map(e -> new Weighted<>(e.index[index], e.weight))
                    .collect(toCollection(ArrayList::new));
            list.sort((o1, o2) -> Float.compare(o1.index, o2.index));
            this.lower[i] = prefixPick(list, totalWeight * (1.0 - percentile)).index;
            this.median[i] = prefixPick(list, totalWeight / 2.0).index;
            this.upper[i] = prefixPick(list, totalWeight * percentile).index;
        }
    }

}
