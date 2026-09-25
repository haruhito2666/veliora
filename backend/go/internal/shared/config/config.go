// Package config loads environment-based configuration shared by all services.
package config

import (
	"os"
	"strconv"
)

// Config holds the settings every service needs.
type Config struct {
	PostgresURL string
	NATSURL     string
	RedisURL    string
	ServiceName string
	Port        string
	Env         string
}

// FromEnv builds a Config from environment variables with sensible dev defaults.
func FromEnv() Config {
	return Config{
		PostgresURL: getenv("DATABASE_URL", "postgres://veliora:veliora@localhost:5432/veliora?sslmode=disable"),
		NATSURL:     getenv("NATS_URL", "nats://localhost:4222"),
		RedisURL:    getenv("REDIS_URL", "redis://localhost:6379"),
		ServiceName: getenv("SERVICE_NAME", "unknown"),
		Port:        getenv("PORT", "8080"),
		Env:         getenv("ENV", "dev"),
	}
}

func getenv(key, def string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return def
}

// GetenvBool reads a boolean env var, defaulting to def.
func GetenvBool(key string, def bool) bool {
	v := os.Getenv(key)
	if v == "" {
		return def
	}
	b, err := strconv.ParseBool(v)
	if err != nil {
		return def
	}
	return b
}
