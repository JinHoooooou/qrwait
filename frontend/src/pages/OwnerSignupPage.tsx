import {useState} from 'react'
import {Link, useNavigate} from 'react-router-dom'
import Button from '../components/Button'
import {login, signUp} from '../api/owner'
import useOwnerStore from '../store/ownerStore'

interface FormValues {
  email: string
  password: string
  passwordConfirm: string
  storeName: string
  address: string
}

const initialForm: FormValues = {
  email: '',
  password: '',
  passwordConfirm: '',
  storeName: '',
  address: '',
}

function validate(form: FormValues): string | null {
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
  if (!emailRegex.test(form.email)) return '유효한 이메일 주소를 입력해 주세요.'
  if (form.password.length < 8) return '비밀번호는 8자 이상이어야 합니다.'
  if (form.password !== form.passwordConfirm) return '비밀번호가 일치하지 않습니다.'
  if (!form.storeName.trim()) return '매장명을 입력해 주세요.'
  if (!form.address.trim()) return '주소를 입력해 주세요.'
  return null
}

function OwnerSignupPage() {
  const navigate = useNavigate()
  const setAuth = useOwnerStore((s) => s.setAuth)
  const [form, setForm] = useState<FormValues>(initialForm)
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setForm((prev) => ({...prev, [e.target.name]: e.target.value}))
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    const validationError = validate(form)
    if (validationError) {
      setError(validationError)
      return
    }

    setSubmitting(true)
    setError(null)
    try {
      await signUp({
        email: form.email,
        password: form.password,
        storeName: form.storeName.trim(),
        address: form.address.trim(),
      })
      const loginRes = await login({email: form.email, password: form.password})
      setAuth({
        accessToken: loginRes.accessToken,
        ownerId: loginRes.ownerId,
        storeId: loginRes.storeId,
      })
      navigate('/owner/onboarding')
    } catch (err) {
      setError(err instanceof Error ? err.message : '회원가입에 실패했습니다.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
      <div style={styles.container}>
        <h1 style={styles.title}>점주 회원가입</h1>

        <form onSubmit={handleSubmit} style={styles.form}>
          <label style={styles.label}>
            이메일
            <input
                style={styles.input}
                type="email"
                name="email"
                value={form.email}
                onChange={handleChange}
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
                name="password"
                value={form.password}
                onChange={handleChange}
                placeholder="8자 이상 입력"
                autoComplete="new-password"
                required
            />
          </label>

          <label style={styles.label}>
            비밀번호 확인
            <input
                style={styles.input}
                type="password"
                name="passwordConfirm"
                value={form.passwordConfirm}
                onChange={handleChange}
                placeholder="비밀번호 재입력"
                autoComplete="new-password"
                required
            />
          </label>

          <label style={styles.label}>
            매장명
            <input
                style={styles.input}
                type="text"
                name="storeName"
                value={form.storeName}
                onChange={handleChange}
                placeholder="예: 맛있는 식당"
                maxLength={50}
                required
            />
          </label>

          <label style={styles.label}>
            주소
            <input
                style={styles.input}
                type="text"
                name="address"
                value={form.address}
                onChange={handleChange}
                placeholder="예: 서울시 강남구 테헤란로 123"
                required
            />
          </label>

          {error && <p style={styles.error}>{error}</p>}

          <Button type="submit" disabled={submitting}>
            {submitting ? '가입 중...' : '회원가입'}
          </Button>
        </form>

        <p style={styles.loginLink}>
          이미 계정이 있으신가요?{' '}
          <Link to="/owner/login" style={styles.link}>로그인</Link>
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
  loginLink: {
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

export default OwnerSignupPage
