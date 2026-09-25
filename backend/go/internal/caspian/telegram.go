package caspian

import (
	"context"
	"fmt"
	"io"
	"net/http"
	"strings"
	"time"
)

// httpNewRequest and httpDo are small indirections so tests can stub the
// transport without importing net/http everywhere.
var (
	httpNewRequest = func(ctx context.Context, method, url string, body io.Reader) (*http.Request, error) {
		return http.NewRequestWithContext(ctx, method, url, body)
	}
	httpDo = func(req *http.Request) (*http.Response, error) {
		return http.DefaultClient.Do(req)
	}
)

// TelegramAdapter sends messages via the Telegram Bot API.
type TelegramAdapter struct {
	token string
}

// NewTelegramAdapter builds the Telegram adapter.
func NewTelegramAdapter(token string) *TelegramAdapter {
	return &TelegramAdapter{token: token}
}

// Channel returns the channel name.
func (a *TelegramAdapter) Channel() string { return ChannelTelegram }

// Send delivers a Telegram message to a chat id or @handle.
func (a *TelegramAdapter) Send(ctx context.Context, to, subject, body string) (string, error) {
	// subject is ignored on Telegram; body is the message.
	payload := fmt.Sprintf(`{"chat_id":%q,"text":%q,"disable_web_page_preview":true}`,
		to, body)
	url := fmt.Sprintf("https://api.telegram.org/bot%s/sendMessage", a.token)
	req, err := httpNewRequest(ctx, "POST", url, strings.NewReader(payload))
	if err != nil {
		return "", err
	}
	req.Header.Set("Content-Type", "application/json")
	resp, err := httpDo(req)
	if err != nil {
		return "", fmt.Errorf("telegram: %w", err)
	}
	defer resp.Body.Close()
	if resp.StatusCode >= 300 {
		b, _ := io.ReadAll(io.LimitReader(resp.Body, 512))
		return "", fmt.Errorf("telegram status %d: %s", resp.StatusCode, strings.TrimSpace(string(b)))
	}
	return fmt.Sprintf("tg-%d", time.Now().UnixNano()), nil
}
