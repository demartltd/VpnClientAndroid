package com.demar.vpn.domain.vpn.tun

data class TunAppRef(val id: String, val name: String)

data class TunGroup(val id: String, val name: String, val apps: List<TunAppRef>) {
  companion object {
    fun idFor(name: String): String = name.trim().lowercase()
  }
}

fun TunSettings.isRulesEnabled(isFree: Boolean): Boolean = isFree || rulesEnabled

fun TunSettings.effectivePackages(groups: List<TunGroup>, isFree: Boolean): List<String> {
  if (!isRulesEnabled(isFree)) return emptyList()
  val fromGroups =
    groups
      .asSequence()
      .filter { isFree || it.id !in disabledGroupIds }
      .flatMap { it.apps.asSequence().map { a -> a.id } }
  return (bypassPackages.asSequence() + fromGroups)
    .map { it.trim() }
    .filter { it.isNotEmpty() }
    .distinct()
    .sorted()
    .toList()
}

fun TunSettings.isGroupEnabled(groupId: String, isFree: Boolean): Boolean =
  isFree || groupId !in disabledGroupIds
