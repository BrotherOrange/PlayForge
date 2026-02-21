/** Sub-agent type -> full display label */
export const AGENT_TYPE_LABELS: Record<string, string> = {
  systemDesigner: 'Systems Designer',
  balancingDesigner: 'Balancing Designer',
  levelDesigner: 'Level Designer',
  narrativeDesigner: 'Narrative Designer',
  combatDesigner: 'Combat Designer',
  technicalDesigner: 'Technical Designer',
  juniorDesigner: 'Junior Designer',
  default: 'General Agent',
};

/** Sub-agent type -> short label (for compact UI) */
export const AGENT_TYPE_SHORT_LABELS: Record<string, string> = {
  systemDesigner: 'Systems',
  balancingDesigner: 'Balancing',
  levelDesigner: 'Level',
  narrativeDesigner: 'Narrative',
  combatDesigner: 'Combat',
  technicalDesigner: 'Technical',
  juniorDesigner: 'Junior',
  default: 'General',
};

/** Sub-agent type -> color */
export const AGENT_TYPE_COLORS: Record<string, string> = {
  systemDesigner: '#00d4ff',
  balancingDesigner: '#f59e0b',
  levelDesigner: '#34d399',
  narrativeDesigner: '#a78bfa',
  combatDesigner: '#ef4444',
  technicalDesigner: '#6366f1',
  juniorDesigner: '#94a3b8',
  default: '#64748b',
};

/** Extract agent type from auto-generated name (e.g. "systemDesigner-a1b2c3d4" -> "systemDesigner") */
export function getAgentTypeFromName(name: string): string {
  const idx = name.lastIndexOf('-');
  return idx > 0 ? name.substring(0, idx) : name;
}

/** Get full display label for an agent name */
export function getAgentLabel(name: string): string {
  const type = getAgentTypeFromName(name);
  return AGENT_TYPE_LABELS[type] || type;
}

/** Get short display label for an agent name */
export function getAgentShortLabel(name: string): string {
  const type = getAgentTypeFromName(name);
  return AGENT_TYPE_SHORT_LABELS[type] || type;
}

/** Get color for an agent name */
export function getAgentColor(name: string): string {
  const type = getAgentTypeFromName(name);
  return AGENT_TYPE_COLORS[type] || '#64748b';
}
