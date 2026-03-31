interface ErrorMessageProps {
  message: string
}

function ErrorMessage({ message }: ErrorMessageProps) {
  return (
    <div
      style={{
        padding: '1rem',
        borderRadius: '0.5rem',
        backgroundColor: '#fef2f2',
        color: '#dc2626',
        fontSize: '0.875rem',
      }}
    >
      {message}
    </div>
  )
}

export default ErrorMessage
