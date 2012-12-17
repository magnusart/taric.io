package io.taric.models

/**
 * Copyright Solvies AB 2012
 * User: magnus
 * Date: 2012-12-17
 * Time: 22:13
 */
sealed trait Data
sealed trait State

case object Uninitialized extends State
case object BrowsingFTP extends State
case object Unencrypting extends State
case object Extracting extends State
case object Parsing extends State
