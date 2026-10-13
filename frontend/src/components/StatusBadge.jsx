import { moderationStatusLabels, reportStatusLabels } from '../labels.js'

// kind: 'moderation' | 'report'
function StatusBadge({ kind = 'moderation', value }) {
  if (!value) return null
  const labels = kind === 'report' ? reportStatusLabels : moderationStatusLabels
  const tone = value.toLowerCase().replace('_', '-')
  return <span className={`badge badge-${tone}`}>{labels[value] || value}</span>
}

export default StatusBadge
