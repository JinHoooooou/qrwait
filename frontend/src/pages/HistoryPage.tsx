import {useEffect, useState} from 'react'
import {useNavigate, useSearchParams} from 'react-router-dom'
import type {TodayWaiting} from '../api/owner'
import {getTodayWaitings} from '../api/owner'
import LoadingSpinner from '../components/LoadingSpinner'
import ErrorMessage from '../components/ErrorMessage'

type TabKey = 'ALL' | 'CURRENT' | 'ENTERED' | 'NO_SHOW' | 'CANCELLED'

const TABS: { key: TabKey; label: string; statuses: TodayWaiting['status'][] }[] = [
  {key: 'ALL', label: '전체', statuses: ['WAITING', 'CALLED', 'ENTERED', 'NO_SHOW', 'CANCELLED']},
  {key: 'CURRENT', label: '등록', statuses: ['WAITING', 'CALLED']},
  {key: 'ENTERED', label: '입장', statuses: ['ENTERED']},
  {key: 'NO_SHOW', label: '노쇼', statuses: ['NO_SHOW']},
  {key: 'CANCELLED', label: '취소', statuses: ['CANCELLED']},
]

const STATUS_LABELS: Record<TodayWaiting['status'], string> = {
  WAITING: '대기',
  CALLED: '호출됨',
  ENTERED: '입장',
  NO_SHOW: '노쇼',
  CANCELLED: '취소',
}

const STATUS_COLORS: Record<TodayWaiting['status'], string> = {
  WAITING: '#374151',
  CALLED: '#d97706',
  ENTERED: '#16a34a',
  NO_SHOW: '#dc2626',
  CANCELLED: '#6b7280',
}

function statusParamToTabKey(param: string | null): TabKey {
  if (param === 'ENTERED') return 'ENTERED'
  if (param === 'NO_SHOW') return 'NO_SHOW'
  if (param === 'CANCELLED') return 'CANCELLED'
  if (param === 'CURRENT') return 'CURRENT'
  return 'ALL'
}

function tabKeyToStatusParam(key: TabKey): string | null {
  if (key === 'ALL') return null
  return key
}

function HistoryPage() {
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  const [entries, setEntries] = useState<TodayWaiting[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const activeTab = statusParamToTabKey(searchParams.get('status'))

  useEffect(() => {
    getTodayWaitings()
        .then(setEntries)
        .catch(() => setError('이력을 불러오지 못했습니다.'))
        .finally(() => setLoading(false))
  }, [])

  const handleTabChange = (key: TabKey) => {
    const param = tabKeyToStatusParam(key)
    if (param === null) {
      setSearchParams({})
    } else {
      setSearchParams({status: param})
    }
  }

  const currentTab = TABS.find((t) => t.key === activeTab)!
  const filtered = entries.filter((e) => currentTab.statuses.includes(e.status))

  const formatTime = (iso: string) => {
    const d = new Date(iso)
    return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
  }

  if (loading) return <LoadingSpinner/>

  return (
      <div style={styles.container}>
        <div style={styles.header}>
          <button style={styles.backBtn} onClick={() => navigate('/owner/dashboard')}>
            ← 대시보드
          </button>
          <h1 style={styles.title}>오늘의 웨이팅</h1>
        </div>

        {error && <ErrorMessage message={error}/>}

        <div style={styles.tabs}>
          {TABS.map(({key, label}) => (
              <button
                  key={key}
                  style={{
                    ...styles.tab,
                    backgroundColor: activeTab === key ? '#3b82f6' : '#f3f4f6',
                    color: activeTab === key ? '#fff' : '#374151',
                  }}
                  onClick={() => handleTabChange(key)}
              >
                {label}
              </button>
          ))}
        </div>

        {filtered.length === 0 ? (
            <p style={styles.empty}>해당 항목이 없습니다.</p>
        ) : (
            <div style={styles.list}>
              {filtered.map((entry) => (
                  <div key={entry.waitingId} style={styles.card}>
                    <span style={styles.number}>#{entry.waitingNumber}</span>
                    <span style={styles.phone}>{entry.phoneNumber}</span>
                    <span style={styles.meta}>{entry.partySize}명</span>
                    <span style={{...styles.status, color: STATUS_COLORS[entry.status]}}>
                      {STATUS_LABELS[entry.status]}
                    </span>
                    <span style={styles.time}>{formatTime(entry.createdAt)}</span>
                  </div>
              ))}
            </div>
        )}
      </div>
  )
}

const styles: Record<string, React.CSSProperties> = {
  container: {
    maxWidth: 480,
    margin: '0 auto',
    padding: '1.5rem',
    display: 'flex',
    flexDirection: 'column',
    gap: '1.25rem',
  },
  header: {
    display: 'flex',
    alignItems: 'center',
    gap: '1rem',
  },
  backBtn: {
    background: 'none',
    border: 'none',
    color: '#3b82f6',
    cursor: 'pointer',
    fontSize: '0.875rem',
    flexShrink: 0,
  },
  title: {
    fontSize: '1.125rem',
    fontWeight: 700,
    margin: 0,
  },
  tabs: {
    display: 'flex',
    gap: '0.375rem',
    flexWrap: 'wrap',
  },
  tab: {
    padding: '0.5rem 0.875rem',
    borderRadius: '0.5rem',
    border: 'none',
    fontSize: '0.8125rem',
    fontWeight: 600,
    cursor: 'pointer',
  },
  empty: {
    color: '#9ca3af',
    fontSize: '0.875rem',
    textAlign: 'center',
    padding: '3rem 0',
  },
  list: {
    display: 'flex',
    flexDirection: 'column',
    gap: '0.5rem',
  },
  card: {
    display: 'flex',
    alignItems: 'center',
    gap: '0.625rem',
    padding: '0.875rem 1rem',
    borderRadius: '0.75rem',
    border: '1px solid #e2e8f0',
    backgroundColor: '#fff',
    fontSize: '0.875rem',
  },
  number: {
    fontWeight: 700,
    color: '#3b82f6',
    minWidth: '2.5rem',
  },
  phone: {
    flex: 1,
    fontWeight: 500,
  },
  meta: {
    color: '#6b7280',
    minWidth: '2rem',
  },
  status: {
    fontWeight: 600,
    minWidth: '3.5rem',
    textAlign: 'right',
  },
  time: {
    color: '#9ca3af',
    minWidth: '2.75rem',
    textAlign: 'right',
  },
}

export default HistoryPage
