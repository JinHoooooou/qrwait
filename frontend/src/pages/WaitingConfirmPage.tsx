import {useEffect} from 'react'
import {useNavigate, useParams} from 'react-router-dom'
import useWaitingStore from '../store/waitingStore'
import Button from '../components/Button'

function WaitingConfirmPage() {
  const navigate = useNavigate()
  const {waitingId} = useParams<{ waitingId: string }>()
  const {waitingNumber, currentRank, estimatedWaitMinutes} = useWaitingStore()

  useEffect(() => {
    if (!waitingNumber) {
      navigate(`/waiting/${waitingId}/status`, {replace: true})
    }
  }, [waitingNumber, waitingId, navigate])

  if (!waitingNumber) return null

  return (
      <div style={styles.container}>
        <div style={styles.badge}>등록 완료</div>

        <div style={styles.card}>
          <p style={styles.label}>내 웨이팅 번호</p>
          <p style={styles.number}>{waitingNumber}</p>
        </div>

        <div style={styles.infoRow}>
          <div style={styles.infoItem}>
            <p style={styles.infoLabel}>현재 대기 순서</p>
            <p style={styles.infoValue}>{currentRank}번째</p>
          </div>
          <div style={styles.infoItem}>
            <p style={styles.infoLabel}>예상 대기시간</p>
            <p style={styles.infoValue}>약 {estimatedWaitMinutes}분</p>
          </div>
        </div>

        <div style={styles.buttons}>
          <Button onClick={() => navigate(`/waiting/${waitingId}/status`)}>
            실시간 현황 보기
          </Button>
          <Button
              variant="secondary"
              onClick={() => navigate(`/waiting/${waitingId}/cancel`)}
          >
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
  badge: {
    backgroundColor: '#dcfce7',
    color: '#16a34a',
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
    gap: '1rem',
  },
  infoItem: {
    flex: 1,
    textAlign: 'center',
    padding: '1rem',
    borderRadius: '0.75rem',
    backgroundColor: '#f8fafc',
    border: '1px solid #e2e8f0',
  },
  infoLabel: {
    fontSize: '0.75rem',
    color: '#6b7280',
    marginBottom: '0.25rem',
  },
  infoValue: {
    fontSize: '1.125rem',
    fontWeight: 600,
  },
  buttons: {
    width: '100%',
    display: 'flex',
    flexDirection: 'column',
    gap: '0.75rem',
  },
}

export default WaitingConfirmPage
