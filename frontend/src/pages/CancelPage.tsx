import {useState} from 'react'
import {useNavigate, useParams} from 'react-router-dom'
import {cancelWaiting} from '../api/waiting'
import {clearWaitingSession} from '../utils/session'
import useWaitingStore from '../store/waitingStore'
import Button from '../components/Button'

function CancelPage() {
  const navigate = useNavigate()
  const {waitingId} = useParams<{ waitingId: string }>()
  const clearWaiting = useWaitingStore((s) => s.clearWaiting)

  const [cancelling, setCancelling] = useState(false)
  const [cancelled, setCancelled] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const handleConfirm = async () => {
    if (!waitingId) return
    setCancelling(true)
    try {
      await cancelWaiting(waitingId)
      clearWaitingSession()
      clearWaiting()
      setCancelled(true)
    } catch {
      setError('취소 처리 중 오류가 발생했습니다. 다시 시도해주세요.')
    } finally {
      setCancelling(false)
    }
  }

  if (cancelled) {
    return (
        <div style={styles.container}>
          <div style={styles.badge}>취소 완료</div>
          <p style={styles.message}>웨이팅이 취소되었습니다.</p>
          <Button onClick={() => navigate('/')}>처음으로</Button>
        </div>
    )
  }

  return (
      <div style={styles.container}>
        <div style={styles.iconWrap}>
          <span style={styles.icon}>!</span>
        </div>
        <p style={styles.title}>웨이팅을 취소하시겠습니까?</p>
        <p style={styles.desc}>취소 후에는 다시 웨이팅을 등록해야 합니다.</p>

        {error && <p style={styles.error}>{error}</p>}

        <div style={styles.buttons}>
          <Button onClick={handleConfirm} disabled={cancelling}>
            {cancelling ? '취소 중...' : '확인'}
          </Button>
          <Button
              variant="secondary"
              onClick={() => navigate(`/waiting/${waitingId}/status`)}
              disabled={cancelling}
          >
            돌아가기
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
  iconWrap: {
    width: 64,
    height: 64,
    borderRadius: '50%',
    backgroundColor: '#fee2e2',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
  },
  icon: {
    fontSize: '2rem',
    fontWeight: 700,
    color: '#dc2626',
  },
  title: {
    fontSize: '1.25rem',
    fontWeight: 700,
    textAlign: 'center',
  },
  desc: {
    fontSize: '0.875rem',
    color: '#6b7280',
    textAlign: 'center',
  },
  error: {
    fontSize: '0.875rem',
    color: '#dc2626',
    textAlign: 'center',
  },
  badge: {
    backgroundColor: '#dcfce7',
    color: '#16a34a',
    padding: '0.375rem 1rem',
    borderRadius: '999px',
    fontSize: '0.875rem',
    fontWeight: 600,
  },
  message: {
    fontSize: '1.125rem',
    fontWeight: 600,
    textAlign: 'center',
  },
  buttons: {
    width: '100%',
    display: 'flex',
    flexDirection: 'column',
    gap: '0.75rem',
  },
}

export default CancelPage
