import {useState} from 'react'
import {useNavigate} from 'react-router-dom'
import Button from '../components/Button'
import {updateStoreSettings} from '../api/owner'
import {getStoreQrUrl} from '../api/waiting'
import useOwnerStore from '../store/ownerStore'

const TOTAL_STEPS = 3

function OnboardingPage() {
  const navigate = useNavigate()
  const storeId = useOwnerStore((s) => s.storeId)
  const [step, setStep] = useState(1)
  const [tableCount, setTableCount] = useState(5)
  const [avgTurnoverMinutes, setAvgTurnoverMinutes] = useState(30)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const qrUrl = storeId ? getStoreQrUrl(storeId) : null

  const handleDownloadQr = async () => {
    if (!qrUrl) return
    const res = await fetch(qrUrl)
    const blob = await res.blob()
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = 'qr-code.png'
    a.click()
    URL.revokeObjectURL(url)
  }

  const handleSaveSettings = async () => {
    setSaving(true)
    setError(null)
    try {
      await updateStoreSettings({tableCount, avgTurnoverMinutes})
      setStep(3)
    } catch (err) {
      setError(err instanceof Error ? err.message : '설정 저장에 실패했습니다.')
    } finally {
      setSaving(false)
    }
  }

  return (
      <div style={styles.container}>
        <div style={styles.stepIndicator}>
          {Array.from({length: TOTAL_STEPS}, (_, i) => (
              <div
                  key={i}
                  style={{
                    ...styles.stepDot,
                    backgroundColor: i + 1 <= step ? '#3b82f6' : '#e5e7eb',
                  }}
              />
          ))}
        </div>

        {step === 1 && (
            <div style={styles.stepContent}>
              <h1 style={styles.title}>QR 코드를 확인하세요</h1>
              <p style={styles.description}>
                손님이 이 QR 코드를 스캔하면 웨이팅 등록 페이지로 이동합니다.
                매장 입구에 인쇄해서 부착해 주세요.
              </p>

              {qrUrl && (
                  <div style={styles.qrCard}>
                    <img src={qrUrl} alt="QR 코드" style={styles.qrImage}/>
                  </div>
              )}

              <Button onClick={handleDownloadQr} variant="secondary">
                PNG 다운로드
              </Button>
              <div style={styles.buttonRow}>
                <Button variant="secondary" onClick={() => navigate('/owner/dashboard')}>건너뛰기</Button>
                <Button onClick={() => setStep(2)}>다음</Button>
              </div>
            </div>
        )}

        {step === 2 && (
            <div style={styles.stepContent}>
              <h1 style={styles.title}>매장 기본 설정</h1>
              <p style={styles.description}>
                예상 대기시간 계산에 사용됩니다. 나중에 설정 페이지에서 변경할 수 있습니다.
              </p>

              <label style={styles.label}>
                테이블 수
                <input
                    style={styles.input}
                    type="number"
                    min={1}
                    max={100}
                    value={tableCount}
                    onChange={(e) => setTableCount(Number(e.target.value))}
                />
              </label>

              <label style={styles.label}>
                팀당 평균 이용시간 (분)
                <input
                    style={styles.input}
                    type="number"
                    min={5}
                    max={300}
                    value={avgTurnoverMinutes}
                    onChange={(e) => setAvgTurnoverMinutes(Number(e.target.value))}
                />
              </label>

              {error && <p style={styles.error}>{error}</p>}

              <div style={styles.buttonRow}>
                <Button variant="secondary" onClick={() => setStep(1)}>이전</Button>
                <Button onClick={handleSaveSettings} disabled={saving}>
                  {saving ? '저장 중...' : '저장 후 다음'}
                </Button>
              </div>
            </div>
        )}

        {step === 3 && (
            <div style={styles.stepContent}>
              <h1 style={styles.title}>대시보드 사용 안내</h1>
              <p style={styles.description}>준비가 완료됐습니다! 대시보드에서 아래 기능을 사용할 수 있습니다.</p>

              <div style={styles.guideList}>
                <div style={styles.guideItem}>
                  <span style={styles.guideIcon}>📋</span>
                  <div>
                    <p style={styles.guideTitle}>실시간 대기 목록</p>
                    <p style={styles.guideDesc}>등록된 손님 목록을 실시간으로 확인하고 호출·입장·노쇼 처리할 수 있습니다.</p>
                  </div>
                </div>
                <div style={styles.guideItem}>
                  <span style={styles.guideIcon}>🏪</span>
                  <div>
                    <p style={styles.guideTitle}>매장 상태 관리</p>
                    <p style={styles.guideDesc}>운영 중 / 브레이크타임 / 만석 / 영업 종료 상태를 변경할 수 있습니다.</p>
                  </div>
                </div>
                <div style={styles.guideItem}>
                  <span style={styles.guideIcon}>📊</span>
                  <div>
                    <p style={styles.guideTitle}>오늘의 통계</p>
                    <p style={styles.guideDesc}>등록·입장·노쇼·취소 건수를 한눈에 확인할 수 있습니다.</p>
                  </div>
                </div>
              </div>

              <div style={styles.buttonRow}>
                <Button variant="secondary" onClick={() => setStep(2)}>이전</Button>
                <Button onClick={() => navigate('/owner/dashboard')}>대시보드 시작하기</Button>
              </div>
            </div>
        )}
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
  stepIndicator: {
    display: 'flex',
    gap: '0.5rem',
    justifyContent: 'center',
  },
  stepDot: {
    width: 8,
    height: 8,
    borderRadius: '50%',
  },
  stepContent: {
    display: 'flex',
    flexDirection: 'column',
    gap: '1.25rem',
  },
  title: {
    fontSize: '1.5rem',
    fontWeight: 700,
  },
  description: {
    fontSize: '0.875rem',
    color: '#6b7280',
    lineHeight: 1.6,
  },
  qrCard: {
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
    fontWeight: 400,
  },
  error: {
    fontSize: '0.875rem',
    color: '#dc2626',
  },
  guideList: {
    display: 'flex',
    flexDirection: 'column',
    gap: '1rem',
  },
  guideItem: {
    display: 'flex',
    gap: '0.75rem',
    alignItems: 'flex-start',
    padding: '1rem',
    borderRadius: '0.75rem',
    backgroundColor: '#f8fafc',
    border: '1px solid #e2e8f0',
  },
  guideIcon: {
    fontSize: '1.25rem',
    flexShrink: 0,
  },
  guideTitle: {
    fontWeight: 600,
    fontSize: '0.875rem',
    marginBottom: '0.25rem',
  },
  guideDesc: {
    fontSize: '0.8rem',
    color: '#6b7280',
    lineHeight: 1.5,
  },
  buttonRow: {
    display: 'flex',
    gap: '0.75rem',
  },
}

export default OnboardingPage
