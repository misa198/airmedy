export type SmartRuleFieldType = 'string' | 'number' | 'boolean'

export interface SmartRuleFieldSpec {
  id: string
  labelKey: string
  type: SmartRuleFieldType
  operators: string[]
}

export interface SmartRule {
  field: string
  op: string
  value: unknown
}

// Mirrors the backend's domain.SmartRuleGroup: a node's own rules and its
// nested groups all combine with each other via `match` ("all" = AND,
// "any" = OR), recursively — this is what lets the rule builder nest
// multiple AND/OR blocks instead of one flat list.
export interface SmartRuleGroup {
  match: 'all' | 'any'
  rules: SmartRule[]
  groups: SmartRuleGroup[]
}

export function emptyGroup(match: 'all' | 'any' = 'all'): SmartRuleGroup {
  return { match, rules: [], groups: [] }
}

// The rule builder UI only ever edits a fixed two-level shape: a root group
// whose own `rules` stay empty and whose `groups` are flat rule-only boxes
// (no further nesting) — clearer to read/build than letting the backend's
// fully-recursive shape nest arbitrarily deep in the UI. This flattens
// whatever shape is stored (bare root rules from before groups existed, or
// deeper nesting) into that two-level shape for editing; saving always
// round-trips through it again.
export function normalizeGroupForEditor(group: SmartRuleGroup): SmartRuleGroup {
  const leaves: SmartRuleGroup[] = []
  // `rules`/`groups` may be missing on data saved by an older backend build
  // that omitted empty-slice JSON keys — default defensively rather than
  // trust the stored shape.
  function collect(node: SmartRuleGroup) {
    const rules = node?.rules ?? []
    const groups = node?.groups ?? []
    if (rules.length > 0) leaves.push({ match: node.match, rules, groups: [] })
    for (const sub of groups) collect(sub)
  }
  collect(group)
  return { match: group?.match ?? 'all', rules: [], groups: leaves }
}

// Mirrors the backend's domain.SmartPlaylistLimit/SmartPlaylistConfig.
export type SmartLimitBy = 'random' | 'album' | 'artist' | 'genre' | 'title' | 'most_played'

export interface SmartPlaylistLimit {
  enabled: boolean
  count: number
  by: SmartLimitBy
}

export interface SmartPlaylistConfig {
  root: SmartRuleGroup
  limit: SmartPlaylistLimit
  live_updating: boolean
}

export function emptyLimit(): SmartPlaylistLimit {
  return { enabled: false, count: 25, by: 'random' }
}

export function emptyConfig(): SmartPlaylistConfig {
  return { root: emptyGroup(), limit: emptyLimit(), live_updating: true }
}

export const SMART_LIMIT_BY_OPTIONS: { value: SmartLimitBy; labelKey: string }[] = [
  { value: 'random', labelKey: 'playlists.smart.limit_by_random' },
  { value: 'album', labelKey: 'playlists.smart.limit_by_album' },
  { value: 'artist', labelKey: 'playlists.smart.limit_by_artist' },
  { value: 'genre', labelKey: 'playlists.smart.limit_by_genre' },
  { value: 'title', labelKey: 'playlists.smart.limit_by_title' },
  { value: 'most_played', labelKey: 'playlists.smart.limit_by_most_played' },
]

// Same flattening rationale as normalizeGroupForEditor, applied to the full
// config. Also handles the pre-limit-feature shape where the parsed JSON
// *was* the bare root group (no `root`/`limit`/`live_updating` wrapper) —
// detected by the presence of `match`, which only a SmartRuleGroup has.
export function normalizeConfigForEditor(parsed: any): SmartPlaylistConfig {
  if (!parsed) return emptyConfig()
  const root: SmartRuleGroup = parsed.root ?? (parsed.match ? parsed : emptyGroup())
  return {
    root: normalizeGroupForEditor(root),
    limit: parsed.limit ?? emptyLimit(),
    live_updating: parsed.live_updating ?? true,
  }
}

// Explicit allowlist mirroring the backend's field/operator whitelist
// (internal/app/playlist/smart_rules.go). The value-editor a RuleRow renders
// is chosen by `type`, not by field id, so adding a new field here never
// requires a new editor component.
export const SMART_PLAYLIST_FIELDS: SmartRuleFieldSpec[] = [
  { id: 'genre', labelKey: 'playlists.smart.field_genre', type: 'string', operators: ['is', 'is_not', 'contains'] },
  { id: 'artist', labelKey: 'playlists.smart.field_artist', type: 'string', operators: ['is', 'is_not', 'contains'] },
  { id: 'year', labelKey: 'playlists.smart.field_year', type: 'number', operators: ['gt', 'lt', 'gte', 'lte', 'between'] },
  { id: 'bpm', labelKey: 'playlists.smart.field_bpm', type: 'number', operators: ['gt', 'lt', 'gte', 'lte', 'between'] },
  { id: 'play_count', labelKey: 'playlists.smart.field_play_count', type: 'number', operators: ['gt', 'lt', 'gte', 'lte'] },
  { id: 'duration', labelKey: 'playlists.smart.field_duration', type: 'number', operators: ['gt', 'lt', 'gte', 'lte', 'between'] },
  { id: 'bitrate', labelKey: 'playlists.smart.field_bitrate', type: 'number', operators: ['gt', 'lt', 'gte', 'lte', 'between'] },
  { id: 'is_favorite', labelKey: 'playlists.smart.field_is_favorite', type: 'boolean', operators: ['is'] },
  { id: 'added_at', labelKey: 'playlists.smart.field_added_at', type: 'number', operators: ['in_last_days'] },
]

export const SMART_RULE_OPERATOR_LABEL_KEYS: Record<string, string> = {
  is: 'playlists.smart.op_is',
  is_not: 'playlists.smart.op_is_not',
  contains: 'playlists.smart.op_contains',
  gt: 'playlists.smart.op_gt',
  lt: 'playlists.smart.op_lt',
  gte: 'playlists.smart.op_gte',
  lte: 'playlists.smart.op_lte',
  between: 'playlists.smart.op_between',
  in_last_days: 'playlists.smart.op_in_last_days',
}

export function getFieldSpec(fieldId: string): SmartRuleFieldSpec | undefined {
  return SMART_PLAYLIST_FIELDS.find(f => f.id === fieldId)
}

export function defaultRuleForField(spec: SmartRuleFieldSpec): SmartRule {
  const op = spec.operators[0]
  let value: unknown = ''
  if (spec.type === 'number') value = op === 'between' ? [0, 0] : 0
  if (spec.type === 'boolean') value = true
  return { field: spec.id, op, value }
}

// Hard cap on rules per playlist — keeps the rule tree (and the WHERE clause
// it compiles to) from growing unbounded.
export const MAX_RULES = 16

// Total leaf rules across every group (root's own `rules` counted too, in
// case a caller bypasses the two-level editor convention). Defaults missing
// rules/groups to empty — see normalizeGroupForEditor for why they can be
// absent on stored data.
export function countRules(group: SmartRuleGroup): number {
  const rules = group?.rules ?? []
  const groups = group?.groups ?? []
  return rules.length + groups.reduce((sum, sub) => sum + countRules(sub), 0)
}

// A rule is valid once its value is well-formed for its field's type/op —
// this is UI-side validation only (the backend allowlist in smart_rules.go
// is the actual security gate); it just stops the user from saving a rule
// that would silently do nothing (empty string match, NaN, or an inverted
// between range).
export function isRuleValid(rule: SmartRule): boolean {
  const spec = getFieldSpec(rule.field)
  if (!spec || !spec.operators.includes(rule.op)) return false

  if (spec.type === 'boolean') return typeof rule.value === 'boolean'

  if (spec.type === 'string') return typeof rule.value === 'string' && rule.value.trim().length > 0

  // number
  if (rule.op === 'between') {
    if (!Array.isArray(rule.value) || rule.value.length !== 2) return false
    const [lo, hi] = rule.value
    return typeof lo === 'number' && typeof hi === 'number' && Number.isFinite(lo) && Number.isFinite(hi) && lo <= hi
  }
  return typeof rule.value === 'number' && Number.isFinite(rule.value)
}

export function isGroupValid(group: SmartRuleGroup): boolean {
  const rules = group?.rules ?? []
  const groups = group?.groups ?? []
  return rules.every(isRuleValid) && groups.every(isGroupValid)
}
