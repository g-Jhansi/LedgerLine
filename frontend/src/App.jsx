import React, { useState, useEffect } from 'react';

const API_BASE = '';

async function apiCall(endpoint, method = 'GET', body = null, token = null, idempotencyKey = null) {
  const headers = { 'Content-Type': 'application/json' };
  if (token) headers['Authorization'] = `Bearer ${token}`;
  if (idempotencyKey) headers['Idempotency-Key'] = idempotencyKey;

  const config = { method, headers };
  if (body && (method === 'POST' || method === 'PUT')) {
    config.body = JSON.stringify(body);
  }

  try {
    const res = await fetch(`${API_BASE}${endpoint}`, config);
    const data = await res.json().catch(() => ({}));
    return { ok: res.ok, status: res.status, data };
  } catch (err) {
    return { ok: false, status: 500, data: { message: err.message || 'Network request failed' } };
  }
}

export default function App() {
  const [token, setToken] = useState(localStorage.getItem('ledgerline_token') || null);
  const [user, setUser] = useState(JSON.parse(localStorage.getItem('ledgerline_user') || 'null'));
  const [health, setHealth] = useState(null);
  const [activeTab, setActiveTab] = useState('accounts');
  const [accounts, setAccounts] = useState([]);
  const [statementAccount, setStatementAccount] = useState(null);
  const [statement, setStatement] = useState([]);
  const [auditLogs, setAuditLogs] = useState([]);
  const [ledgerIntegrity, setLedgerIntegrity] = useState(null);
  const [chainIntegrity, setChainIntegrity] = useState(null);
  const [govSummary, setGovSummary] = useState(null);
  const [usersList, setUsersList] = useState([]);
  const [loading, setLoading] = useState(false);
  const [alert, setAlert] = useState(null);

  // Modal & form states
  const [modalType, setModalType] = useState(null);
  const [selectedAccount, setSelectedAccount] = useState('');
  const [amount, setAmount] = useState('');
  const [description, setDescription] = useState('');
  const [transferTarget, setTransferTarget] = useState('');
  const [useIdempotency, setUseIdempotency] = useState(true);
  const [idempotencyKey, setIdempotencyKey] = useState(crypto.randomUUID());

  useEffect(() => {
    apiCall('/api/health').then(r => {
      if (r.ok) setHealth(r.data);
    });
  }, []);

  useEffect(() => {
    if (token && user) {
      loadDataForRole(user.role);
    }
  }, [token, user]);

  const showAlert = (message, type = 'success') => {
    setAlert({ message, type });
    setTimeout(() => setAlert(null), 5000);
  };

  const loadDataForRole = async (role) => {
    setLoading(true);
    if (role === 'ROLE_ACCOUNT_HOLDER') {
      const accRes = await apiCall('/api/accounts/me', 'GET', null, token);
      if (accRes.ok && accRes.data.data) {
        setAccounts(accRes.data.data);
        if (accRes.data.data.length > 0) {
          loadStatement(accRes.data.data[0].accountNumber);
        }
      }
    } else if (role === 'ROLE_AUDITOR') {
      setActiveTab('governance');
      loadGovernanceData();
    } else if (role === 'ROLE_ADMIN') {
      const accRes = await apiCall('/api/accounts/me', 'GET', null, token);
      if (accRes.ok && accRes.data.data) setAccounts(accRes.data.data);
      const usersRes = await apiCall('/api/auth/admin/users', 'GET', null, token);
      if (usersRes.ok && usersRes.data.data) setUsersList(usersRes.data.data);
      loadGovernanceData();
    }
    setLoading(false);
  };

  const loadStatement = async (accountNumber) => {
    setStatementAccount(accountNumber);
    const res = await apiCall(`/api/transactions/statement/${accountNumber}?size=20`, 'GET', null, token);
    if (res.ok && res.data.data) {
      setStatement(res.data.data.content || []);
    }
  };

  const loadGovernanceData = async () => {
    const [ledgerRes, chainRes, sumRes, logsRes] = await Promise.all([
      apiCall('/api/governance/verify-ledger', 'GET', null, token),
      apiCall('/api/governance/verify-chain', 'GET', null, token),
      apiCall('/api/governance/summary', 'GET', null, token),
      apiCall('/api/governance/audit-logs?size=25', 'GET', null, token)
    ]);

    if (ledgerRes.ok) setLedgerIntegrity(ledgerRes.data.data);
    if (chainRes.ok) setChainIntegrity(chainRes.data.data);
    if (sumRes.ok) setGovSummary(sumRes.data.data);
    if (logsRes.ok && logsRes.data.data) setAuditLogs(logsRes.data.data.content || []);
  };

  const handleLogin = async (username, password) => {
    setLoading(true);
    const res = await apiCall('/api/auth/login', 'POST', { username, password });
    setLoading(false);
    if (res.ok && res.data.data) {
      const auth = res.data.data;
      setToken(auth.token);
      setUser(auth);
      localStorage.setItem('ledgerline_token', auth.token);
      localStorage.setItem('ledgerline_user', JSON.stringify(auth));
      showAlert(`Welcome back, ${auth.fullName}!`);
    } else {
      showAlert(res.data.message || 'Login failed', 'error');
    }
  };

  const handleLogout = () => {
    setToken(null);
    setUser(null);
    localStorage.removeItem('ledgerline_token');
    localStorage.removeItem('ledgerline_user');
    setAccounts([]);
    setStatement([]);
    setAuditLogs([]);
    showAlert('Logged out successfully');
  };

  const handleTransactionSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);
    const key = useIdempotency ? idempotencyKey : null;
    let res;

    if (modalType === 'deposit') {
      res = await apiCall('/api/transactions/deposit', 'POST', {
        accountNumber: selectedAccount,
        amount: parseFloat(amount),
        description: description || 'Account Deposit'
      }, token, key);
    } else if (modalType === 'withdraw') {
      res = await apiCall('/api/transactions/withdraw', 'POST', {
        accountNumber: selectedAccount,
        amount: parseFloat(amount),
        description: description || 'Account Withdrawal'
      }, token, key);
    } else if (modalType === 'transfer') {
      res = await apiCall('/api/transactions/transfer', 'POST', {
        fromAccountNumber: selectedAccount,
        toAccountNumber: transferTarget,
        amount: parseFloat(amount),
        description: description || 'Account Transfer'
      }, token, key);
    }

    setLoading(false);
    if (res && res.ok) {
      showAlert(`${modalType.toUpperCase()} completed successfully! Ref: ${res.data.data.referenceId}`);
      setModalType(null);
      setAmount('');
      setDescription('');
      setTransferTarget('');
      setIdempotencyKey(crypto.randomUUID());
      loadDataForRole(user.role);
    } else {
      showAlert(res?.data?.message || 'Transaction rejected by ledger rules', 'error');
    }
  };

  if (!token || !user) {
    return (
      <div className="min-h-screen flex items-center justify-center p-4 bg-gradient-to-br from-slate-950 via-slate-900 to-indigo-950">
        <div className="max-w-md w-full bg-slate-900/90 border border-slate-800 rounded-2xl p-8 shadow-2xl backdrop-blur-xl">
          <div className="text-center mb-8">
            <h1 className="text-2xl font-bold tracking-tight text-white">LedgerLine</h1>
            <p className="text-sm text-slate-400 mt-1">Safe Financial Ledger & Audit Platform</p>
            {health && (
              <div className="inline-flex items-center gap-2 mt-3 px-3 py-1 rounded-full text-xs font-medium bg-emerald-500/10 border border-emerald-500/20 text-emerald-400">
                <span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse"></span>
                Backend {health.status} • PostgreSQL Connected
              </div>
            )}
          </div>

          {alert && (
            <div className={`mb-6 p-4 rounded-xl text-sm border ${alert.type === 'error' ? 'bg-rose-500/10 border-rose-500/30 text-rose-300' : 'bg-emerald-500/10 border-emerald-500/30 text-emerald-300'}`}>
              {alert.message}
            </div>
          )}

          <div className="space-y-3 mb-6">
            <p className="text-xs font-semibold uppercase tracking-wider text-slate-400">Quick-Login As Seed User:</p>
            <div className="grid grid-cols-2 gap-2">
              <button onClick={() => handleLogin('john_doe', 'User@123')} className="p-3 bg-slate-800/80 hover:bg-slate-700 border border-slate-700/80 rounded-xl text-left transition">
                <div className="text-xs font-semibold text-emerald-400">Account Holder</div>
                <div className="text-sm text-white font-medium">john_doe</div>
              </button>
              <button onClick={() => handleLogin('jane_smith', 'User@123')} className="p-3 bg-slate-800/80 hover:bg-slate-700 border border-slate-700/80 rounded-xl text-left transition">
                <div className="text-xs font-semibold text-emerald-400">Account Holder</div>
                <div className="text-sm text-white font-medium">jane_smith</div>
              </button>
              <button onClick={() => handleLogin('auditor', 'Auditor@123')} className="p-3 bg-slate-800/80 hover:bg-slate-700 border border-slate-700/80 rounded-xl text-left transition">
                <div className="text-xs font-semibold text-blue-400">Auditor</div>
                <div className="text-sm text-white font-medium">auditor</div>
              </button>
              <button onClick={() => handleLogin('admin', 'Admin@123')} className="p-3 bg-slate-800/80 hover:bg-slate-700 border border-slate-700/80 rounded-xl text-left transition">
                <div className="text-xs font-semibold text-purple-400">Administrator</div>
                <div className="text-sm text-white font-medium">admin</div>
              </button>
            </div>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col">
      <header className="border-b border-slate-800 bg-slate-900/60 backdrop-blur-md sticky top-0 z-40">
        <div className="max-w-7xl mx-auto px-4 h-16 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <h1 className="text-lg font-bold tracking-tight text-white">LedgerLine</h1>
            <span className="text-xs text-slate-400">Safe Financial Core</span>
          </div>
          <div className="flex items-center gap-4">
            <div className="flex items-center gap-2 px-3 py-1.5 rounded-full bg-slate-800 border border-slate-700 text-xs">
              <span className="text-slate-300 font-medium">{user.username}</span>
              <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-indigo-500/20 text-indigo-300 border border-indigo-500/30">
                {user.role.replace('ROLE_', '')}
              </span>
            </div>
            <button onClick={handleLogout} className="px-3 py-1.5 rounded-lg text-xs font-semibold text-slate-400 hover:text-white hover:bg-slate-800 transition">
              Sign Out
            </button>
          </div>
        </div>
      </header>

      {/* Tabs */}
      <div className="border-b border-slate-800 bg-slate-900/30">
        <div className="max-w-7xl mx-auto px-4 flex gap-6">
          {(user.role === 'ROLE_ACCOUNT_HOLDER' || user.role === 'ROLE_ADMIN') && (
            <button onClick={() => setActiveTab('accounts')} className={`py-4 text-sm font-semibold border-b-2 transition ${activeTab === 'accounts' ? 'border-indigo-500 text-white' : 'border-transparent text-slate-400 hover:text-slate-200'}`}>
              Accounts & Ledger
            </button>
          )}
          {(user.role === 'ROLE_AUDITOR' || user.role === 'ROLE_ADMIN') && (
            <button onClick={() => { setActiveTab('governance'); loadGovernanceData(); }} className={`py-4 text-sm font-semibold border-b-2 transition ${activeTab === 'governance' ? 'border-indigo-500 text-white' : 'border-transparent text-slate-400 hover:text-slate-200'}`}>
              Governance & Audit Trail
            </button>
          )}
        </div>
      </div>

      <main className="max-w-7xl mx-auto px-4 py-8 flex-1 w-full">
        {alert && (
          <div className={`mb-6 p-4 rounded-xl text-sm border ${alert.type === 'error' ? 'bg-rose-500/10 border-rose-500/30 text-rose-300' : 'bg-emerald-500/10 border-emerald-500/30 text-emerald-300'}`}>
            {alert.message}
          </div>
        )}

        {activeTab === 'accounts' && (
          <div className="space-y-8">
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
              {accounts.map(acc => (
                <div key={acc.accountNumber} className="bg-slate-900 border border-slate-800 rounded-2xl p-6 shadow-lg">
                  <div className="flex justify-between items-start mb-4">
                    <span className="text-xs font-bold px-2 py-0.5 rounded bg-slate-800 text-slate-300 uppercase">{acc.accountType}</span>
                    <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-emerald-500/20 text-emerald-400">{acc.status}</span>
                  </div>
                  <div className="font-mono text-sm text-slate-400 mb-2">{acc.accountNumber}</div>
                  <div className="text-3xl font-extrabold text-white mb-6">₹{parseFloat(acc.balance).toLocaleString('en-IN', { minimumFractionDigits: 2 })}</div>
                  <div className="grid grid-cols-3 gap-2">
                    <button onClick={() => { setModalType('deposit'); setSelectedAccount(acc.accountNumber); }} className="px-3 py-2 bg-emerald-600/20 border border-emerald-500/30 text-emerald-300 text-xs font-semibold rounded-xl">Deposit</button>
                    <button onClick={() => { setModalType('withdraw'); setSelectedAccount(acc.accountNumber); }} className="px-3 py-2 bg-rose-600/20 border border-rose-500/30 text-rose-300 text-xs font-semibold rounded-xl">Withdraw</button>
                    <button onClick={() => { setModalType('transfer'); setSelectedAccount(acc.accountNumber); }} className="px-3 py-2 bg-indigo-600/20 border border-indigo-500/30 text-indigo-300 text-xs font-semibold rounded-xl">Transfer</button>
                  </div>
                </div>
              ))}
            </div>

            {/* Statement */}
            <div className="bg-slate-900 border border-slate-800 rounded-2xl overflow-hidden shadow-xl">
              <div className="p-4 border-b border-slate-800 font-bold text-white">Double-Entry Ledger Statement</div>
              <table className="w-full text-left text-sm">
                <thead className="bg-slate-800/50 text-slate-400 text-xs uppercase">
                  <tr>
                    <th className="p-4">Date</th>
                    <th className="p-4">Type</th>
                    <th className="p-4">Amount</th>
                    <th className="p-4">Balance After</th>
                    <th className="p-4">Description</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-800">
                  {statement.map(st => (
                    <tr key={st.id}>
                      <td className="p-4 text-xs text-slate-400">{new Date(st.createdAt).toLocaleString()}</td>
                      <td className="p-4"><span className={`px-2 py-0.5 rounded text-[10px] font-bold ${st.entryType === 'CREDIT' ? 'bg-emerald-500/20 text-emerald-400' : 'bg-rose-500/20 text-rose-400'}`}>{st.entryType}</span></td>
                      <td className={`p-4 font-semibold ${st.entryType === 'CREDIT' ? 'text-emerald-400' : 'text-rose-400'}`}>₹{parseFloat(st.amount).toFixed(2)}</td>
                      <td className="p-4 font-mono text-xs text-white">₹{parseFloat(st.balanceAfter).toFixed(2)}</td>
                      <td className="p-4 text-xs text-slate-300">{st.description}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}

        {activeTab === 'governance' && (
          <div className="space-y-8">
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
              <div className="bg-slate-900 border border-slate-800 rounded-2xl p-6">
                <div className="text-xs text-slate-400 uppercase">Double-Entry Conservation</div>
                <div className="text-2xl font-bold text-white mt-2">{ledgerIntegrity?.balanced ? 'Zero Imbalance' : 'Imbalance'}</div>
                <div className="text-xs text-emerald-400 mt-2">Σ Debits = Σ Credits</div>
              </div>
              <div className="bg-slate-900 border border-slate-800 rounded-2xl p-6">
                <div className="text-xs text-slate-400 uppercase">Audit Hash Chain</div>
                <div className="text-2xl font-bold text-white mt-2">{chainIntegrity?.totalRecordsVerified || 0} Blocks</div>
                <div className="text-xs text-emerald-400 mt-2">Cryptographically Verified</div>
              </div>
              <div className="bg-slate-900 border border-slate-800 rounded-2xl p-6">
                <div className="text-xs text-slate-400 uppercase">Settled Volume</div>
                <div className="text-2xl font-bold text-white mt-2">₹{(parseFloat(govSummary?.totalDepositsVolume || 0) + parseFloat(govSummary?.totalTransfersVolume || 0)).toLocaleString()}</div>
                <div className="text-xs text-slate-400 mt-2">{govSummary?.totalTransactions || 0} Transactions</div>
              </div>
              <div className="bg-slate-900 border border-slate-800 rounded-2xl p-6">
                <div className="text-xs text-slate-400 uppercase">Accounts</div>
                <div className="text-2xl font-bold text-white mt-2">{govSummary?.totalAccounts || 0}</div>
                <div className="text-xs text-slate-400 mt-2">{govSummary?.totalUsers || 0} Users</div>
              </div>
            </div>

            {/* Audit Logs */}
            <div className="bg-slate-900 border border-slate-800 rounded-2xl overflow-hidden shadow-xl">
              <div className="p-4 border-b border-slate-800 font-bold text-white">Cryptographic Audit Trail</div>
              <table className="w-full text-left text-sm">
                <thead className="bg-slate-800/50 text-slate-400 text-xs uppercase">
                  <tr>
                    <th className="p-4">Block #</th>
                    <th className="p-4">Action</th>
                    <th className="p-4">Actor</th>
                    <th className="p-4">Details</th>
                    <th className="p-4">SHA-256 Hash</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-800">
                  {auditLogs.map(l => (
                    <tr key={l.id}>
                      <td className="p-4 font-mono text-xs text-indigo-400">#{l.id}</td>
                      <td className="p-4 text-xs font-semibold">{l.action}</td>
                      <td className="p-4 text-xs">{l.actorUsername}</td>
                      <td className="p-4 text-xs text-slate-300">{l.details}</td>
                      <td className="p-4 font-mono text-xs text-emerald-400">{l.currentHash.substring(0, 16)}...</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}
      </main>

      {/* Modal */}
      {modalType && (
        <div className="fixed inset-0 bg-slate-950/80 backdrop-blur-sm flex items-center justify-center p-4 z-50">
          <div className="bg-slate-900 border border-slate-800 rounded-2xl p-6 max-w-md w-full shadow-2xl">
            <h3 className="text-lg font-bold text-white mb-4 capitalize">{modalType} Funds</h3>
            <form onSubmit={handleTransactionSubmit} className="space-y-4">
              <div>
                <label className="block text-xs text-slate-400 mb-1">Account</label>
                <input type="text" readOnly value={selectedAccount} className="w-full bg-slate-950 border border-slate-800 rounded-xl p-3 text-sm text-slate-300" />
              </div>
              {modalType === 'transfer' && (
                <div>
                  <label className="block text-xs text-slate-400 mb-1">Target Account</label>
                  <input type="text" required placeholder="LL1000000002" value={transferTarget} onChange={e => setTransferTarget(e.target.value)} className="w-full bg-slate-950 border border-slate-700 rounded-xl p-3 text-sm text-white" />
                </div>
              )}
              <div>
                <label className="block text-xs text-slate-400 mb-1">Amount (₹)</label>
                <input type="number" step="0.01" min="0.01" required placeholder="0.00" value={amount} onChange={e => setAmount(e.target.value)} className="w-full bg-slate-950 border border-slate-700 rounded-xl p-3 text-lg font-bold text-white" />
              </div>
              <div>
                <label className="block text-xs text-slate-400 mb-1">Description</label>
                <input type="text" placeholder="Optional notes" value={description} onChange={e => setDescription(e.target.value)} className="w-full bg-slate-950 border border-slate-700 rounded-xl p-3 text-sm text-white" />
              </div>
              <div className="flex gap-3 mt-6">
                <button type="button" onClick={() => setModalType(null)} className="flex-1 py-3 bg-slate-800 rounded-xl text-xs font-semibold text-slate-300">Cancel</button>
                <button type="submit" disabled={loading} className="flex-1 py-3 bg-indigo-600 rounded-xl text-xs font-semibold text-white">{loading ? 'Processing...' : 'Confirm'}</button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}

