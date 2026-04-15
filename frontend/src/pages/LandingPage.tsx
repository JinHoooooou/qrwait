import {useEffect, useRef, useState} from 'react'
import {useNavigate, useSearchParams} from 'react-router-dom'
import type {StoreResponse, StoreWaitingStatus} from '../api/waiting'
import {getStore, getStoreWaitingStatus, registerWaiting} from '../api/waiting'
import useWaitingStore from '../store/waitingStore'
import {getWaitingSession, saveWaitingSession} from '../utils/session'
import Button from '../components/Button'
import LoadingSpinner from '../components/LoadingSpinner'
import ErrorMessage from '../components/ErrorMessage'

const formatPhoneNumber = (value: string): string => {
  const digits = value.replace(/\D/g, '')
  if (digits.length <= 3) return digits
  if (digits.length <= 7) return `${digits.slice(0, 3)}-${digits.slice(3)}`
  return `${digits.slice(0, 3)}-${digits.slice(3, 7)}-${digits.slice(7, 11)}`
}

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
  const [waitingStatus, setWaitingStatus] = useState<StoreWaitingStatus | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const [phoneNumber, setPhoneNumber] = useState('')
  const [partySize, setPartySize] = useState(1)
  const [agreed, setAgreed] = useState(false)
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

    Promise.all([getStore(storeId), getStoreWaitingStatus(storeId)])
        .then(([storeData, statusData]) => {
          setStore(storeData)
          setWaitingStatus(statusData)
        })
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
    if (!storeId || !phoneNumber.trim() || !agreed) return

    setSubmitting(true)
    try {
      const res = await registerWaiting(storeId, {
        phoneNumber: phoneNumber.trim(),
        partySize,
      })
      setWaiting({
        waitingId: res.waitingId,
        waitingNumber: res.waitingNumber,
        storeId,
        currentRank: res.currentRank,
        totalWaiting: res.totalWaiting,
        estimatedWaitMinutes: res.estimatedWaitMinutes,
      })
      saveWaitingSession(res.waitingId, storeId, res.waitingNumber)
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

        {waitingStatus && (
            <div style={styles.statusBox}>
              <span style={styles.statusTeam}>현재 대기 {waitingStatus.totalWaiting}팀</span>
              <span style={styles.statusDot}>·</span>
              <span style={styles.statusTime}>예상 {waitingStatus.estimatedWaitMinutes}분</span>
            </div>
        )}

        {statusMessage ? (
            <div style={styles.unavailableBox}>
              <p style={styles.unavailableMessage}>{statusMessage}</p>
              <p style={styles.unavailableHint}>잠시 후 다시 확인해 주세요.</p>
            </div>
        ) : (
            <>
              <p style={styles.subtitle}>웨이팅 등록</p>
              <form onSubmit={handleSubmit} style={styles.form}>
                <label style={styles.label}>
                  전화번호
                  <input
                      style={styles.input}
                      type="tel"
                      value={phoneNumber}
                      onChange={(e) => setPhoneNumber(formatPhoneNumber(e.target.value))}
                      maxLength={13}
                      placeholder="010-XXXX-XXXX"
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

                <label style={styles.consentLabel}>
                  <input
                      type="checkbox"
                      checked={agreed}
                      onChange={(e) => setAgreed(e.target.checked)}
                      style={styles.checkbox}
                  />
                  <span style={styles.consentText}>
                    전화번호는 웨이팅 호출 알림 목적으로만 사용됩니다.
                  </span>
                </label>

                <Button type="submit" disabled={submitting || !agreed}>
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
    marginBottom: '0.75rem',
  },
  statusBox: {
    display: 'flex',
    alignItems: 'center',
    gap: '0.5rem',
    padding: '0.75rem 1rem',
    borderRadius: '0.75rem',
    backgroundColor: '#eff6ff',
    border: '1px solid #bfdbfe',
    marginBottom: '1.5rem',
    fontSize: '0.9375rem',
    fontWeight: 600,
    color: '#1d4ed8',
  },
  statusDot: {
    color: '#93c5fd',
  },
  statusTeam: {},
  statusTime: {},
  unavailableBox: {
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
  unavailableMessage: {
    fontSize: '1.125rem',
    fontWeight: 600,
    color: '#374151',
  },
  unavailableHint: {
    fontSize: '0.875rem',
    color: '#9ca3af',
  },
  subtitle: {
    color: '#6b7280',
    marginBottom: '1.5rem',
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
  consentLabel: {
    display: 'flex',
    alignItems: 'flex-start',
    gap: '0.5rem',
    cursor: 'pointer',
  },
  checkbox: {
    marginTop: '0.125rem',
    width: 16,
    height: 16,
    flexShrink: 0,
    cursor: 'pointer',
  },
  consentText: {
    fontSize: '0.8125rem',
    color: '#6b7280',
    lineHeight: 1.5,
  },
}

export default LandingPage
