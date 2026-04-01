import {useEffect, useRef, useState} from 'react'
import {useNavigate, useParams} from 'react-router-dom'
import useWaitingStore from '../store/waitingStore'
import {getWaiting} from '../api/waiting'
import {getWaitingSession} from '../utils/session'
import Button from '../components/Button'

type ConnectionStatus = 'connecting' | 'connected' | 'error'

function WaitingStatusPage() {
  const navigate = useNavigate()
  const {waitingId} = useParams<{ waitingId: string }>()

  const {waitingNumber, storeId, currentRank, totalWaiting, estimatedWaitMinutes, updateStatus} =
      useWaitingStore()

  const session = getWaitingSession()
  const resolvedStoreId = storeId ?? session?.storeId ?? null
  const resolvedWaitingNumber = waitingNumber ?? session?.waitingNumber ?? null

  const [connectionStatus, setConnectionStatus] = useState<ConnectionStatus>('connecting')
  const [showCalledModal, setShowCalledModal] = useState(false)
  const eventSourceRef = useRef<EventSource | null>(null)

  // 세션도 없으면 루트로 리다이렉트
  useEffect(() => {
    if (!resolvedStoreId) {
      navigate('/', {replace: true})
    }
  }, [resolvedStoreId, navigate])

  // 초기 상태 로드
  useEffect(() => {
    if (!waitingId) return
    getWaiting(waitingId).then((res) => {
      updateStatus({
        currentRank: res.currentRank,
        totalWaiting: res.totalWaiting,
        estimatedWaitMinutes: res.estimatedWaitMinutes,
      })
    })
  }, [waitingId, updateStatus])

  // SSE 연결
  useEffect(() => {
    if (!waitingId || !resolvedStoreId) return

    const es = new EventSource(`/api/waitings/${waitingId}/stream?storeId=${resolvedStoreId}`)
    eventSourceRef.current = es

    es.onopen = () => setConnectionStatus('connected')
    es.onerror = () => setConnectionStatus('error')

    es.addEventListener('waiting-update', () => {
      getWaiting(waitingId).then((res) => {
        updateStatus({
          currentRank: res.currentRank,
          totalWaiting: res.totalWaiting,
          estimatedWaitMinutes: res.estimatedWaitMinutes,
        })
      })
    })

    es.addEventListener('called', () => {
      setShowCalledModal(true)
    })

    return () => {
      es.close()
    }
  }, [waitingId, resolvedStoreId, updateStatus])

  if (!resolvedStoreId) return null

  return (
      <div style={styles.container}>
        {showCalledModal && (
            <div style={styles.overlay}>
              <div style={styles.modal}>
                <p style={styles.modalTitle}>입장해 주세요!</p>
                <p style={styles.modalDesc}>순서가 되었습니다. 지금 입장해 주세요.</p>
                <Button onClick={() => setShowCalledModal(false)}>확인</Button>
              </div>
            </div>
        )}

        <div style={{...styles.statusBadge, ...statusBadgeVariant[connectionStatus]}}>
          {connectionStatus === 'connected'
              ? '● 실시간 업데이트 중'
              : connectionStatus === 'error'
                  ? '● 연결 오류'
                  : '● 연결 중...'}
        </div>

        <div style={styles.card}>
          <p style={styles.label}>내 웨이팅 번호</p>
          <p style={styles.number}>{resolvedWaitingNumber ?? '-'}</p>
        </div>

        <div style={styles.infoRow}>
          <div style={styles.infoItem}>
            <p style={styles.infoLabel}>현재 대기 순서</p>
            <p style={styles.infoValue}>{currentRank != null ? `${currentRank}번째` : '-'}</p>
          </div>
          <div style={styles.infoItem}>
            <p style={styles.infoLabel}>앞 대기 팀</p>
            <p style={styles.infoValue}>{totalWaiting != null ? `${totalWaiting}팀` : '-'}</p>
          </div>
          <div style={styles.infoItem}>
            <p style={styles.infoLabel}>예상 대기시간</p>
            <p style={styles.infoValue}>
              {estimatedWaitMinutes != null ? `약 ${estimatedWaitMinutes}분` : '-'}
            </p>
          </div>
        </div>

        <div style={styles.buttons}>
          <Button variant="secondary" onClick={() => navigate(`/waiting/${waitingId}/cancel`)}>
            웨이팅 취소
          </Button>
        </div>
      </div>
  )
}

const statusBadgeVariant: Record<string, React.CSSProperties> = {
  connected: {backgroundColor: '#dcfce7', color: '#16a34a'},
  connecting: {backgroundColor: '#fef9c3', color: '#ca8a04'},
  error: {backgroundColor: '#fee2e2', color: '#dc2626'},
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
  statusBadge: {
    padding: '0.375rem 1rem',
    borderRadius: '999px',
    fontSize: '0.875rem',
    fontWeight: 600,
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
  infoRow: {
    width: '100%',
    display: 'flex',
    gap: '0.75rem',
  },
  infoItem: {
    flex: 1,
    textAlign: 'center',
    padding: '1rem 0.5rem',
    borderRadius: '0.75rem',
    backgroundColor: '#f8fafc',
    border: '1px solid #e2e8f0',
  },
  infoLabel: {
    fontSize: '0.7rem',
    color: '#6b7280',
    marginBottom: '0.25rem',
  },
  infoValue: {
    fontSize: '1rem',
    fontWeight: 600,
  },
  buttons: {
    width: '100%',
  },
  overlay: {
    position: 'fixed',
    inset: 0,
    backgroundColor: 'rgba(0,0,0,0.5)',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    zIndex: 100,
  },
  modal: {
    backgroundColor: '#fff',
    borderRadius: '1rem',
    padding: '2rem',
    maxWidth: 320,
    width: '90%',
    display: 'flex',
    flexDirection: 'column',
    gap: '1rem',
    textAlign: 'center',
  },
  modalTitle: {
    fontSize: '1.5rem',
    fontWeight: 700,
    color: '#1d4ed8',
  },
  modalDesc: {
    fontSize: '0.875rem',
    color: '#6b7280',
  },
}

export default WaitingStatusPage
