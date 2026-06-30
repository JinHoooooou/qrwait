import {useCallback, useEffect, useState} from 'react'
import {useNavigate, useParams} from 'react-router-dom'
import useWaitingStore from '../store/waitingStore'
import {getWaiting} from '../api/waiting'
import {clearWaitingSession, getWaitingSession} from '../utils/session'
import {useWaitingSse} from '../hooks/useWaitingSse'
import Button from '../components/Button'

function WaitingCalledPage() {
  const navigate = useNavigate()
  const {waitingId} = useParams<{ waitingId: string }>()

  const {waitingNumber, storeId, clearWaiting} = useWaitingStore()
  const session = getWaitingSession()
  const resolvedStoreId = storeId ?? session?.storeId ?? null
  const resolvedWaitingNumber = waitingNumber ?? session?.waitingNumber ?? null

  const [initialized, setInitialized] = useState(false)
  const [ended, setEnded] = useState(false)

  // 입장완료/취소/노쇼로 더 이상 CALLED가 아닌 경우 종료 처리
  const endSession = useCallback(() => {
    clearWaitingSession()
    clearWaiting()
    setEnded(true)
  }, [clearWaiting])

  const refresh = useCallback(() => {
    if (!waitingId) return
    getWaiting(waitingId)
        .then((res) => {
          if (res.status === 'WAITING') {
            // 아직 호출 전 (비정상 진입) → 실시간 현황으로
            navigate(`/waiting/${waitingId}/status`, {replace: true})
          }
          // CALLED면 그대로 유지
        })
        .catch((err: unknown) => {
          const status = (err as { status?: number }).status
          if (status === 404) endSession()
          // 일시적 오류는 무시 (다음 이벤트/새로고침 때 재시도)
        })
        .finally(() => setInitialized(true))
  }, [waitingId, navigate, endSession])

  useEffect(() => {
    refresh()
  }, [refresh])

  useWaitingSse(waitingId, resolvedStoreId, {
    enabled: !!waitingId && !!resolvedStoreId && !ended,
    onUpdated: refresh,
  })

  if (!initialized) return null

  if (ended) {
    return (
        <div style={styles.container}>
          <div style={styles.endedIcon}>✓</div>
          <p style={styles.endedTitle}>웨이팅이 종료되었습니다</p>
          <p style={styles.endedDesc}>입장이 완료되었거나 취소된 웨이팅입니다.</p>
          <Button onClick={() => navigate('/')}>처음으로</Button>
        </div>
    )
  }

  return (
      <div style={styles.container}>
        <div style={styles.callBadge}>입장해 주세요!</div>

        <div style={styles.card}>
          <p style={styles.label}>내 웨이팅 번호</p>
          <p style={styles.number}>{resolvedWaitingNumber ?? '-'}</p>
        </div>

        <p style={styles.desc}>순서가 되었습니다. 지금 입장해 주세요.</p>

        <div style={styles.buttons}>
          <Button variant="secondary" onClick={() => navigate(`/waiting/${waitingId}/cancel`)}>
            웨이팅 취소
          </Button>
          <Button variant="secondary" disabled>
            미루기 (준비 중)
          </Button>
        </div>
      </div>
  )
}

const styles: Record<string, React.CSSProperties> = {
  container: {
    maxWidth: 480,
    margin: '0 auto',
    padding: '2rem 1.5rem',
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    gap: '1.5rem',
  },
  callBadge: {
    backgroundColor: '#dbeafe',
    color: '#1d4ed8',
    padding: '0.5rem 1.25rem',
    borderRadius: '999px',
    fontSize: '1rem',
    fontWeight: 700,
  },
  card: {
    width: '100%',
    textAlign: 'center',
    padding: '2rem',
    borderRadius: '1rem',
    backgroundColor: '#f8fafc',
    border: '1px solid #e2e8f0',
  },
  label: {
    fontSize: '0.875rem',
    color: '#6b7280',
    marginBottom: '0.5rem',
  },
  number: {
    fontSize: '4rem',
    fontWeight: 700,
    color: '#1d4ed8',
    lineHeight: 1,
  },
  desc: {
    fontSize: '0.875rem',
    color: '#6b7280',
    textAlign: 'center',
  },
  buttons: {
    width: '100%',
    display: 'flex',
    flexDirection: 'column',
    gap: '0.75rem',
  },
  endedIcon: {
    width: 64,
    height: 64,
    borderRadius: '50%',
    backgroundColor: '#f3f4f6',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    fontSize: '2rem',
    color: '#6b7280',
  },
  endedTitle: {
    fontSize: '1.25rem',
    fontWeight: 700,
    textAlign: 'center',
  },
  endedDesc: {
    fontSize: '0.875rem',
    color: '#6b7280',
    textAlign: 'center',
  },
}

export default WaitingCalledPage
