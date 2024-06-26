/**
 * Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"). You may not
 * use this file except in compliance with the License. A copy of the License
 * is located at
 *
 *     http://aws.amazon.com/apache2.0/
 *
 * or in the "license" file accompanying this file. This file is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 *
 */

package com.amazon.deequ.suggestions.rules

import com.amazon.deequ.constraints.Constraint.completenessConstraint
import com.amazon.deequ.profiles.ColumnProfile
import com.amazon.deequ.suggestions.CommonConstraintSuggestion
import com.amazon.deequ.suggestions.ConstraintSuggestion
import com.amazon.deequ.suggestions.rules.RetainCompletenessRule._
import com.amazon.deequ.suggestions.rules.interval.ConfidenceIntervalStrategy.defaultIntervalStrategy
import com.amazon.deequ.suggestions.rules.interval.ConfidenceIntervalStrategy

/**
  * If a column is incomplete in the sample, we model its completeness as a binomial variable,
  * estimate a confidence interval and use this to define a lower bound for the completeness
  *
  * @param minCompleteness : minimum completeness threshold to determine if rule should be applied
  * @param maxCompleteness : maximum completeness threshold to determine if rule should be applied
  */
case class RetainCompletenessRule(
  minCompleteness: Double = defaultMinCompleteness,
  maxCompleteness: Double = defaultMaxCompleteness,
  intervalStrategy: ConfidenceIntervalStrategy = defaultIntervalStrategy
) extends ConstraintRule[ColumnProfile] {
  override def shouldBeApplied(profile: ColumnProfile, numRecords: Long): Boolean = {
    profile.completeness > minCompleteness && profile.completeness < maxCompleteness
  }

  override def candidate(profile: ColumnProfile, numRecords: Long): ConstraintSuggestion = {
    val targetCompleteness = intervalStrategy.calculateTargetConfidenceInterval(
      profile.completeness,
      numRecords
    ).lowerBound

    val constraint = completenessConstraint(profile.column, _ >= targetCompleteness)

    val boundInPercent = ((1.0 - targetCompleteness) * 100).toInt

    val description = s"'${profile.column}' has less than $boundInPercent% missing values"

    CommonConstraintSuggestion(
      constraint,
      profile.column,
      "Completeness: " + profile.completeness.toString,
      description,
      this,
      s""".hasCompleteness("${profile.column}", _ >= $targetCompleteness,
         | Some("It should be above $targetCompleteness!"))"""
        .stripMargin.replaceAll("\n", "")
    )
  }

  override val ruleDescription: String = "If a column is incomplete in the sample, " +
    "we model its completeness as a binomial variable, estimate a confidence interval " +
    "and use this to define a lower bound for the completeness"
}

object RetainCompletenessRule {
  private val defaultMinCompleteness: Double = 0.2
  private val defaultMaxCompleteness: Double = 1.0
}
