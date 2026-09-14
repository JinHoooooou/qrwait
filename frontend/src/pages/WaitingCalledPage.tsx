import {useCallback, useEffect, useState} from 'react'
import {useNavigate, useParams} from 'react-router-dom'
import useWaitingStore from '../store/waitingStore'
import {getStore, getWaiting} from '../api/waiting'
import {clearWaitingSession, getWaitingSession} from '../utils/session'
import {useWaitingSse} from '../hooks/useWaitingSse'
import {WAITING_ENDED_MESSAGES, type WaitingEndedReason} from '../utils/waitingEndedMessages'
import Button from '../components/Button'

function WaitingCalledPage() {
  const navigate = useNavigate()
  const {waitingId} = useParams<{ waitingId: string }>()

  const {waitingNumber, storeId, clearWaiting} = useWaitingStore()
  const session = getWaitingSession()
  const resolvedStoreId = storeId ?? session?.storeId ?? null
  const resolvedWaitingNumber = waitingNumber ?? session?.waitingNumber ?? null
  // 세션 종료(clearWaiting) 이후에도 "처음으로" 버튼이 매장을 기억하도록 마운트 시점 값을 보존
  const [capturedStoreId] = useState(resolvedStoreId)
  const goToStart = () => navigate(capturedStoreId ? `/wait?storeId=${capturedStoreId}` : '/wait')

  const [initialized, setInitialized] = useState(false)
  const [endedReason, setEndedReason] = useState<WaitingEndedReason | null>(null)
  const [graceDeadline, setGraceDeadline] = useState<string | null>(null)
  const [remainingSeconds, setRemainingSeconds] = useState<number | null>(null)
  const [storeName, setStoreName] = useState<string | null>(null)

  useEffect(() => {
    if (!resolvedStoreId) return
    getStore(resolvedStoreId).then((res) => setStoreName(res.name)).catch(() => {
      // 매장명은 부가 정보이므로 조회 실패해도 화면은 정상 진행
    })
  }, [resolvedStoreId])

  // 입장완료/취소/노쇼로 더 이상 CALLED가 아닌 경우 종료 처리
  const endSession = useCallback((reason: WaitingEndedReason) => {
    clearWaitingSession()
    clearWaiting()
    setEndedReason(reason)
  }, [clearWaiting])

  const refresh = useCallback(() => {
    if (!waitingId) return
    getWaiting(waitingId)
        .then((res) => {
          if (res.status === 'WAITING') {
            // 아직 호출 전 (비정상 진입) → 실시간 현황으로
            navigate(`/waiting/${waitingId}/status`, {replace: true})
            return
          }
          if (res.status !== 'CALLED') {
            endSession(res.status)
            return
          }
          setGraceDeadline(res.graceDeadline)
        })
        .catch((err: unknown) => {
          const status = (err as { status?: number }).status
          if (status === 404) endSession('NOT_FOUND')
          // 일시적 오류는 무시 (다음 이벤트/새로고침 때 재시도)
        })
        .finally(() => setInitialized(true))
  }, [waitingId, navigate, endSession])

  useEffect(() => {
    refresh()
  }, [refresh])

  useEffect(() => {
    if (!graceDeadline) return
    const tick = () => {
      const diff = Math.floor((new Date(graceDeadline).getTime() - Date.now()) / 1000)
      setRemainingSeconds(diff > 0 ? diff : 0)
    }
    tick()
    const timer = setInterval(tick, 1000)
    return () => clearInterval(timer)
  }, [graceDeadline])

  useWaitingSse(waitingId, resolvedStoreId, {
    enabled: !!waitingId && !!resolvedStoreId && !endedReason,
    onUpdated: refresh,
    onPostponed: () => navigate(`/waiting/${waitingId}/status`, {replace: true}),
  })

  if (!initialized) return null

  if (endedReason) {
    const message = WAITING_ENDED_MESSAGES[endedReason]
    return (
        <div style={styles.container}>
          <div style={styles.endedIcon}>{endedReason === 'ENTERED' ? '🎉' : '✓'}</div>
          <p style={styles.endedTitle}>{message.title}</p>
          <p style={styles.endedDesc}>{message.desc}</p>
          <Button onClick={goToStart}>처음으로</Button>
        </div>
    )
  }

  return (
      <div style={styles.container}>
        <div style={styles.callBadge}>입장해 주세요!</div>

        {storeName && <p style={styles.storeName}>{storeName}</p>}

        <div style={styles.card}>
          <p style={styles.label}>내 웨이팅 번호</p>
          <p style={styles.number}>{resolvedWaitingNumber ?? '-'}</p>
        </div>

        <p style={styles.desc}>순서가 되었습니다. 지금 입장해 주세요.</p>

        {remainingSeconds !== null && (
            <p style={remainingSeconds > 0 ? styles.graceDesc : styles.graceExpiredDesc}>
              {remainingSeconds > 0
                  ? `${Math.floor(remainingSeconds / 60)}분 ${remainingSeconds % 60}초 안에 입장해 주세요`
                  : '입장 시간이 지났습니다. 매장에 문의해 주세요.'}
            </p>
        )}

        <div style={styles.buttons}>
          <Button variant="secondary" onClick={() => navigate(`/waiting/${waitingId}/cancel`)}>
            웨이팅 취소
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
  storeName: {
    fontSize: '1.125rem',
    fontWeight: 700,
    textAlign: 'center',
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
  graceDesc: {
    fontSize: '0.875rem',
    fontWeight: 600,
    color: '#d97706',
    textAlign: 'center',
  },
  graceExpiredDesc: {
    fontSize: '0.875rem',
    fontWeight: 600,
    color: '#dc2626',
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
