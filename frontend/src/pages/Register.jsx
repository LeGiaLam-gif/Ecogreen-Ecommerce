import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

const Register = () => {
  const [username, setUsername] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const { register } = useAuth();
  const navigate = useNavigate();

  const handleRegister = async (e) => {
    e.preventDefault();
    setError('');
    setSubmitting(true);
    try {
      await register(username, email, password);
      navigate('/login');
    } catch (err) {
      setError(err.friendlyMessage || 'Registration failed.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div style={{ maxWidth: '400px', margin: '50px auto', padding: '20px', border: '1px solid #ccc', borderRadius: '10px', backgroundColor: '#fff' }}>
      <h2 style={{ textAlign: 'center', marginBottom: '20px' }}>Create an account</h2>

      {error && <p style={{ color: '#c62828', textAlign: 'center' }}>{error}</p>}

      <form onSubmit={handleRegister} style={{ display: 'flex', flexDirection: 'column', gap: '15px' }}>
        <div>
          <label style={{ display: 'block', marginBottom: '5px' }}>Username</label>
          <input type="text" value={username} onChange={(e) => setUsername(e.target.value)}
            style={{ width: '100%', padding: '10px', borderRadius: '5px', border: '1px solid #ccc', boxSizing: 'border-box' }} required />
        </div>

        <div>
          <label style={{ display: 'block', marginBottom: '5px' }}>Email</label>
          <input type="email" value={email} onChange={(e) => setEmail(e.target.value)}
            style={{ width: '100%', padding: '10px', borderRadius: '5px', border: '1px solid #ccc', boxSizing: 'border-box' }} required />
        </div>

        <div>
          <label style={{ display: 'block', marginBottom: '5px' }}>Password</label>
          <input type="password" value={password} onChange={(e) => setPassword(e.target.value)}
            style={{ width: '100%', padding: '10px', borderRadius: '5px', border: '1px solid #ccc', boxSizing: 'border-box' }} required />
        </div>

        <button type="submit" className="btn-primary" disabled={submitting}
          style={{ padding: '10px', width: '100%', border: 'none', borderRadius: '5px', cursor: 'pointer', fontSize: '1rem' }}>
          {submitting ? 'Creating account...' : 'Sign up'}
        </button>
      </form>
    </div>
  );
};

export default Register;
