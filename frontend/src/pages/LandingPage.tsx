import {useEffect, useRef, useState} from 'react'
import {useNavigate, useSearchParams} from 'react-router-dom'
import type {StoreResponse} from '../api/waiting'
import {getStore, registerWaiting} from '../api/waiting'
import useWaitingStore from '../store/waitingStore'
import {getWaitingSession, saveWaitingSession} from '../utils/session'
import Button from '../components/Button'
import LoadingSpinner from '../components/LoadingSpinner'
import ErrorMessage from '../components/ErrorMessage'

const STATUS_MESSAGES: Record<string, string> = {
  BREAK: '현재 브레이크타임입니다.',
  FULL: '현재 만석입니다.',
  CLOSED: '오늘 영업이 종료되었습니다.',
}

function LandingPage() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const storeId = searchParams.get('storeId')

  const [store, setStore] = useState<StoreResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const [visitorName, setVisitorName] = useState('')
  const [partySize, setPartySize] = useState(1)
  const [submitting, setSubmitting] = useState(false)

  const setWaiting = useWaitingStore((s) => s.setWaiting)
  const esRef = useRef<EventSource | null>(null)

  useEffect(() => {
    const session = getWaitingSession()
    if (session) {
      navigate(`/waiting/${session.waitingId}/status`, {replace: true})
      return
    }

    if (!storeId) {
      setError('유효하지 않은 QR 코드입니다.')
      setLoading(false)
      return
    }

    getStore(storeId)
        .then(setStore)
        .catch(() => setError('매장 정보를 불러올 수 없습니다.'))
        .finally(() => setLoading(false))
  }, [storeId, navigate])

  useEffect(() => {
    if (!storeId) return

    const es = new EventSource(`/api/stores/${storeId}/stream`)
    esRef.current = es

    es.addEventListener('store-status-changed', (e) => {
      try {
        const data = JSON.parse(e.data)
        setStore((prev) => prev ? {...prev, status: data.status} : prev)
      } catch {
        // 파싱 실패 시 무시
      }
    })

    return () => {
      es.close()
      esRef.current = null
    }
  }, [storeId])

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!storeId || !visitorName.trim()) return

    setSubmitting(true)
    try {
      const res = await registerWaiting(storeId, {
        visitorName: visitorName.trim(),
        partySize,
      })
      setWaiting({
        waitingId: res.waitingId,
        waitingNumber: res.waitingNumber,
        waitingToken: res.waitingToken,
        storeId,
        currentRank: res.currentRank,
        totalWaiting: res.totalWaiting,
        estimatedWaitMinutes: res.estimatedWaitMinutes,
      })
      saveWaitingSession(res.waitingId, res.waitingToken, storeId, res.waitingNumber)
      navigate(`/waiting/${res.waitingId}`)
    } catch {
      setError('웨이팅 등록에 실패했습니다. 다시 시도해주세요.')
    } finally {
      setSubmitting(false)
    }
  }

  if (loading) return <LoadingSpinner/>
  if (error) return <div style={styles.container}><ErrorMessage message={error}/></div>

  const statusMessage = store?.status ? STATUS_MESSAGES[store.status] : null

  return (
      <div style={styles.container}>
        <h1 style={styles.storeName}>{store?.name}</h1>

        {statusMessage ? (
            <div style={styles.statusBox}>
              <p style={styles.statusMessage}>{statusMessage}</p>
              <p style={styles.statusHint}>잠시 후 다시 확인해 주세요.</p>
            </div>
        ) : (
            <>
              <p style={styles.subtitle}>웨이팅 등록</p>
              <form onSubmit={handleSubmit} style={styles.form}>
                <label style={styles.label}>
                  이름
                  <input
                style={styles.input}
                type="text"
                value={visitorName}
                onChange={(e) => setVisitorName(e.target.value)}
                maxLength={50}
                placeholder="이름을 입력하세요"
                required
                  />
                </label>

                <label style={styles.label}>
                  인원수
                  <div style={styles.stepper}>
                    <button
                  type="button"
                  style={styles.stepBtn}
                  onClick={() => setPartySize((n) => Math.max(1, n - 1))}
                    >
                      −
                    </button>
                    <span style={styles.stepValue}>{partySize}명</span>
                    <button
                  type="button"
                  style={styles.stepBtn}
                  onClick={() => setPartySize((n) => Math.min(10, n + 1))}
                    >
                      +
                    </button>
                  </div>
                </label>

                <Button type="submit" disabled={submitting}>
                  {submitting ? '등록 중...' : '웨이팅 등록'}
                </Button>
              </form>
            </>
        )}
      </div>
  )
}

const styles: Record<string, React.CSSProperties> = {
  container: {
    maxWidth: 480,
    margin: '0 auto',
    padding: '2rem 1.5rem',
  },
  storeName: {
    fontSize: '1.5rem',
    fontWeight: 700,
    marginBottom: '0.25rem',
  },
  subtitle: {
    color: '#6b7280',
    marginBottom: '2rem',
  },
  statusBox: {
    marginTop: '2rem',
    padding: '2rem',
    borderRadius: '1rem',
    backgroundColor: '#f8fafc',
    border: '1px solid #e2e8f0',
    textAlign: 'center',
    display: 'flex',
    flexDirection: 'column',
    gap: '0.5rem',
  },
  statusMessage: {
    fontSize: '1.125rem',
    fontWeight: 600,
    color: '#374151',
  },
  statusHint: {
    fontSize: '0.875rem',
    color: '#9ca3af',
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
    outline: 'none',
  },
  stepper: {
    display: 'flex',
    alignItems: 'center',
    gap: '1rem',
  },
  stepBtn: {
    width: 44,
    height: 44,
    borderRadius: '0.5rem',
    border: '1px solid #d1d5db',
    background: '#f9fafb',
    fontSize: '1.25rem',
    cursor: 'pointer',
  },
  stepValue: {
    fontSize: '1.125rem',
    fontWeight: 600,
    minWidth: '3rem',
    textAlign: 'center',
  },
}

export default LandingPage
