import {useState} from 'react'
import {Link, useNavigate} from 'react-router-dom'
import Button from '../components/Button'
import {login} from '../api/owner'
import useOwnerStore from '../store/ownerStore'

function OwnerLoginPage() {
  const navigate = useNavigate()
  const setAuth = useOwnerStore((s) => s.setAuth)
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      const res = await login({email, password})
      setAuth({accessToken: res.accessToken, ownerId: res.ownerId, storeId: res.storeId})
      navigate('/owner/dashboard')
    } catch (err) {
      setError(err instanceof Error ? err.message : '로그인에 실패했습니다.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
      <div style={styles.container}>
        <h1 style={styles.title}>점주 로그인</h1>

        <form onSubmit={handleSubmit} style={styles.form}>
          <label style={styles.label}>
            이메일
            <input
                style={styles.input}
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="example@email.com"
                autoComplete="email"
                required
            />
          </label>

          <label style={styles.label}>
            비밀번호
            <input
                style={styles.input}
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="비밀번호 입력"
                autoComplete="current-password"
                required
            />
          </label>

          {error && <p style={styles.error}>{error}</p>}

          <Button type="submit" disabled={submitting}>
            {submitting ? '로그인 중...' : '로그인'}
          </Button>
        </form>

        <p style={styles.signupLink}>
          아직 계정이 없으신가요?{' '}
          <Link to="/owner/signup" style={styles.link}>회원가입</Link>
        </p>
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
  form: {
    display: 'flex',
    flexDirection: 'column',
    gap: '1.25rem',
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
  signupLink: {
    textAlign: 'center',
    fontSize: '0.875rem',
    color: '#6b7280',
  },
  link: {
    color: '#3b82f6',
    textDecoration: 'none',
    fontWeight: 600,
  },
}

export default OwnerLoginPage
