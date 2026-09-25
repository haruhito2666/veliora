package caspian

import (
	"context"
	"crypto/tls"
	"fmt"
	"net"
	"net/smtp"
	"strings"
	"time"
)

// EmailAdapter sends email via SMTP (or Resend API when ResendKey is set).
type EmailAdapter struct {
	cfg Config
}

// NewEmailAdapter builds the email adapter.
func NewEmailAdapter(cfg Config) *EmailAdapter {
	return &EmailAdapter{cfg: cfg}
}

// Channel returns the channel name.
func (a *EmailAdapter) Channel() string { return ChannelEmail }

// Send delivers an email. With ResendKey it uses the Resend API; otherwise
// SMTP. Returns the external message id.
func (a *EmailAdapter) Send(ctx context.Context, to, subject, body string) (string, error) {
	if a.cfg.ResendKey != "" {
		return a.sendResend(ctx, to, subject, body)
	}
	return a.sendSMTP(to, subject, body)
}

func (a *EmailAdapter) sendSMTP(to, subject, body string) (string, error) {
	from := a.cfg.SMTPFrom
	if from == "" {
		from = a.cfg.SMTPUser
	}
	msg := fmt.Sprintf("From: %s\r\nTo: %s\r\nSubject: %s\r\nMIME-Version: 1.0\r\nContent-Type: text/plain; charset=utf-8\r\n\r\n%s",
		from, to, subject, body)

	addr := net.JoinHostPort(a.cfg.SMTPHost, fmt.Sprintf("%d", a.cfg.SMTPPort))
	var auth smtp.Auth
	if a.cfg.SMTPUser != "" {
		auth = smtp.PlainAuth("", a.cfg.SMTPUser, a.cfg.SMTPPassword, a.cfg.SMTPHost)
	}

	// Connect with optional TLS.
	conn, err := net.DialTimeout("tcp", addr, 10*time.Second)
	if err != nil {
		return "", fmt.Errorf("smtp dial: %w", err)
	}
	client, err := smtp.NewClient(conn, a.cfg.SMTPHost)
	if err != nil {
		return "", fmt.Errorf("smtp client: %w", err)
	}
	defer client.Close()

	// Start TLS when available (port 587 usually requires STARTTLS).
	if ok, _ := client.Extension("STARTTLS"); ok {
		if err := client.StartTLS(&tls.Config{ServerName: a.cfg.SMTPHost}); err != nil {
			return "", fmt.Errorf("starttls: %w", err)
		}
	}
	if auth != nil {
		if err := client.Auth(auth); err != nil {
			return "", fmt.Errorf("smtp auth: %w", err)
		}
	}
	if err := client.Mail(from); err != nil {
		return "", fmt.Errorf("mail from: %w", err)
	}
	if err := client.Rcpt(to); err != nil {
		return "", fmt.Errorf("rcpt: %w", err)
	}
	w, err := client.Data()
	if err != nil {
		return "", fmt.Errorf("data: %w", err)
	}
	if _, err := w.Write([]byte(msg)); err != nil {
		return "", err
	}
	if err := w.Close(); err != nil {
		return "", err
	}
	if err := client.Quit(); err != nil {
		return "", err
	}
	return "smtp-" + fmt.Sprintf("%d", time.Now().UnixNano()), nil
}

func (a *EmailAdapter) sendResend(ctx context.Context, to, subject, body string) (string, error) {
	payload := fmt.Sprintf(`{"from":%q,"to":[%q],"subject":%q,"text":%q}`,
		a.cfg.SMTPFrom, to, subject, body)
	req, err := httpNewRequest(ctx, "POST", "https://api.resend.com/emails",
		strings.NewReader(payload))
	if err != nil {
		return "", err
	}
	req.Header.Set("Authorization", "Bearer "+a.cfg.ResendKey)
	req.Header.Set("Content-Type", "application/json")
	resp, err := httpDo(req)
	if err != nil {
		return "", fmt.Errorf("resend: %w", err)
	}
	defer resp.Body.Close()
	if resp.StatusCode >= 300 {
		return "", fmt.Errorf("resend status %d", resp.StatusCode)
	}
	return "resend-" + fmt.Sprintf("%d", time.Now().UnixNano()), nil
}
