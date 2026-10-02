import React, { useState, useRef, useEffect } from 'react';
import './LiveChatWidget.css';

const BOT_RESPONSES = [
  'Xin chào! Cảm ơn bạn đã liên hệ EcoGreen. Chúng tôi sẽ phản hồi trong vòng 24 giờ.',
  'Bạn có thể gửi câu hỏi về sản phẩm, đổi trả hoặc vận chuyển. Chúng tôi luôn sẵn sàng hỗ trợ!',
  'Cảm ơn bạn! Tin nhắn đã được ghi nhận. Nhân viên CSKH sẽ phản hồi sớm nhất có thể.',
];

const LiveChatWidget = () => {
  const [isOpen, setIsOpen] = useState(false);
  const [messages, setMessages] = useState([
    { from: 'bot', text: '👋 Xin chào! Tôi là trợ lý EcoGreen. Tôi có thể giúp gì cho bạn?' },
  ]);
  const [input, setInput] = useState('');
  const [botIdx, setBotIdx] = useState(0);
  const endRef = useRef(null);

  useEffect(() => {
    if (isOpen) endRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, isOpen]);

  const sendMessage = () => {
    const text = input.trim();
    if (!text) return;
    setMessages((prev) => [...prev, { from: 'user', text }]);
    setInput('');
    // Bot trả lời tự động sau 800ms
    setTimeout(() => {
      setMessages((prev) => [
        ...prev,
        { from: 'bot', text: BOT_RESPONSES[botIdx % BOT_RESPONSES.length] },
      ]);
      setBotIdx((i) => i + 1);
    }, 800);
  };

  const handleKey = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); sendMessage(); }
  };

  return (
    <div className="chat-widget-root">
      {/* Chat Window */}
      {isOpen && (
        <div className="chat-window" role="dialog" aria-label="EcoGreen Live Chat">
          <div className="chat-header">
            <div className="chat-avatar">🌿</div>
            <div className="chat-header-info">
              <strong>EcoGreen Hỗ Trợ</strong>
              <span className="chat-status">● Hoạt động</span>
            </div>
            <button className="chat-close-btn" onClick={() => setIsOpen(false)} aria-label="Đóng chat">✕</button>
          </div>

          <div className="chat-messages">
            {messages.map((msg, i) => (
              <div key={i} className={`chat-msg chat-msg--${msg.from}`}>
                {msg.from === 'bot' && <span className="chat-msg-avatar">🌿</span>}
                <div className="chat-bubble">{msg.text}</div>
              </div>
            ))}
            <div ref={endRef} />
          </div>

          <div className="chat-footer">
            <input
              value={input}
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={handleKey}
              placeholder="Nhập tin nhắn..."
              className="chat-input"
              autoFocus
            />
            <button className="chat-send-btn" onClick={sendMessage} aria-label="Gửi">➤</button>
          </div>
        </div>
      )}

      {/* FAB Button */}
      <button
        className={`chat-fab ${isOpen ? 'chat-fab--open' : ''}`}
        onClick={() => setIsOpen((o) => !o)}
        aria-label="Mở chat hỗ trợ"
      >
        {isOpen ? '✕' : '💬'}
        {!isOpen && <span className="chat-fab-label">Hỗ trợ</span>}
      </button>
    </div>
  );
};

export default LiveChatWidget;
