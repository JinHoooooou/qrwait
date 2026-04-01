import {useState} from 'react'
import {createStore, getStoreQrUrl} from '../api/waiting'
import Button from '../components/Button'

function OwnerPage() {
  const [storeName, setStoreName] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [result, setResult] = useState<{ storeId: string; name: string; qrUrl: string } | null>(null)

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!storeName.trim()) return

    setSubmitting(true)
    setError(null)
    try {
      const res = await createStore({name: storeName.trim()})
      setResult(res)
    } catch (err) {
      setError(err instanceof Error ? err.message : '매장 등록에 실패했습니다.')
    } finally {
      setSubmitting(false)
    }
  }

  const handleReset = () => {
    setResult(null)
    setStoreName('')
  }

  if (result) {
    return (
        <div style={styles.container}>
          <div style={styles.badge}>등록 완료</div>
          <p style={styles.storeName}>{result.name}</p>

          <div style={styles.qrCard}>
            <img
                src={getStoreQrUrl(result.storeId)}
                alt="QR 코드"
                style={styles.qrImage}
            />
          </div>

          <div style={styles.infoBox}>
            <p style={styles.infoLabel}>QR 스캔 URL</p>
            <p style={styles.infoValue}>{result.qrUrl}</p>
          </div>

          <div style={styles.infoBox}>
            <p style={styles.infoLabel}>매장 ID</p>
            <p style={styles.infoValue}>{result.storeId}</p>
          </div>

          <Button variant="secondary" onClick={handleReset}>
            새 매장 등록
          </Button>
        </div>
    )
  }

  return (
      <div style={styles.container}>
        <h1 style={styles.title}>매장 등록</h1>
        <p style={styles.subtitle}>매장명을 입력하면 QR 코드가 자동 생성됩니다.</p>

        <form onSubmit={handleSubmit} style={styles.form}>
          <label style={styles.label}>
            매장명
            <input
                style={styles.input}
                type="text"
                value={storeName}
                onChange={(e) => setStoreName(e.target.value)}
                placeholder="예: 맛있는 식당"
                maxLength={50}
                required
            />
          </label>

          {error && <p style={styles.error}>{error}</p>}

          <Button type="submit" disabled={submitting}>
            {submitting ? '등록 중...' : 'QR 코드 생성'}
          </Button>
        </form>
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
    gap: '1.5rem',
  },
  title: {
    fontSize: '1.5rem',
    fontWeight: 700,
  },
  subtitle: {
    fontSize: '0.875rem',
    color: '#6b7280',
    marginTop: '-1rem',
  },
  form: {
    display: 'flex',
    flexDirection: 'column',
    gap: '1.5rem',
  },
  label: {
    display: 'flex',
    flexDirection: 'column',
    gap: '0.5rem',
    fontWeight: 600,
    fontSize: '0.875rem',
  },
  input: {
    padding: '0.75rem',
    borderRadius: '0.5rem',
    border: '1px solid #d1d5db',
    fontSize: '1rem',
  },
  error: {
    fontSize: '0.875rem',
    color: '#dc2626',
  },
  badge: {
    alignSelf: 'flex-start',
    backgroundColor: '#dcfce7',
    color: '#16a34a',
    padding: '0.375rem 1rem',
    borderRadius: '999px',
    fontSize: '0.875rem',
    fontWeight: 600,
  },
  storeName: {
    fontSize: '1.5rem',
    fontWeight: 700,
  },
  qrCard: {
    width: '100%',
    display: 'flex',
    justifyContent: 'center',
    padding: '1.5rem',
    borderRadius: '1rem',
    backgroundColor: '#f8fafc',
    border: '1px solid #e2e8f0',
  },
  qrImage: {
    width: 200,
    height: 200,
  },
  infoBox: {
    padding: '1rem',
    borderRadius: '0.75rem',
    backgroundColor: '#f8fafc',
    border: '1px solid #e2e8f0',
    wordBreak: 'break-all',
  },
  infoLabel: {
    fontSize: '0.75rem',
    color: '#6b7280',
    marginBottom: '0.25rem',
  },
  infoValue: {
    fontSize: '0.875rem',
    fontWeight: 500,
    color: '#111827',
  },
}

export default OwnerPage
