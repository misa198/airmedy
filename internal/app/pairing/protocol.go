package pairing

import (
	"bytes"
	"crypto/ed25519"
	"encoding/base64"
	"encoding/binary"
	"fmt"
)

const (
	protocolVersion = 1
	requestType     = "pair.request"
	responseType    = "pair.response"
	contextRequest  = "airmedy.mobile-pairing.request.v1"
	contextResponse = "airmedy.mobile-pairing.response.v1"
)

type handshakeRequest struct {
	Version          int    `json:"version"`
	Type             string `json:"type"`
	RequestID        string `json:"request_id"`
	DesktopID        string `json:"desktop_id"`
	DesktopPublicKey string `json:"desktop_public_key"`
	MobileID         string `json:"mobile_id"`
	MobileName       string `json:"mobile_name"`
	MobilePlatform   string `json:"mobile_platform"`
	MobilePublicKey  string `json:"mobile_public_key"`
	Nonce            string `json:"nonce"`
	IssuedAt         int64  `json:"issued_at"`
	Signature        string `json:"signature"`
}

type handshakeResponse struct {
	Version      int    `json:"version"`
	Type         string `json:"type"`
	RequestID    string `json:"request_id"`
	Decision     string `json:"decision"`
	DesktopID    string `json:"desktop_id"`
	DesktopNonce string `json:"desktop_nonce"`
	IssuedAt     int64  `json:"issued_at"`
	Signature    string `json:"signature"`
}

func decodeRaw(value string, length int, field string) ([]byte, error) {
	b, err := base64.RawURLEncoding.DecodeString(value)
	if err != nil || len(b) != length {
		return nil, fmt.Errorf("invalid %s", field)
	}
	return b, nil
}

func writeString(buf *bytes.Buffer, value string) {
	_ = binary.Write(buf, binary.BigEndian, uint16(len(value)))
	buf.WriteString(value)
}

func writeBytes(buf *bytes.Buffer, value []byte) {
	_ = binary.Write(buf, binary.BigEndian, uint16(len(value)))
	buf.Write(value)
}

func requestSigningInput(request handshakeRequest) ([]byte, error) {
	desktopKey, err := decodeRaw(request.DesktopPublicKey, ed25519.PublicKeySize, "desktop_public_key")
	if err != nil {
		return nil, err
	}
	mobileKey, err := decodeRaw(request.MobilePublicKey, ed25519.PublicKeySize, "mobile_public_key")
	if err != nil {
		return nil, err
	}
	nonce, err := decodeRaw(request.Nonce, 32, "nonce")
	if err != nil {
		return nil, err
	}
	var buf bytes.Buffer
	writeString(&buf, contextRequest)
	buf.WriteByte(protocolVersion)
	writeString(&buf, requestType)
	writeString(&buf, request.RequestID)
	writeString(&buf, request.DesktopID)
	writeBytes(&buf, desktopKey)
	writeString(&buf, request.MobileID)
	writeString(&buf, request.MobileName)
	writeString(&buf, request.MobilePlatform)
	writeBytes(&buf, mobileKey)
	writeBytes(&buf, nonce)
	_ = binary.Write(&buf, binary.BigEndian, request.IssuedAt)
	return buf.Bytes(), nil
}

func responseSigningInput(response handshakeResponse, request handshakeRequest, desktopPublicKey []byte) ([]byte, error) {
	mobileKey, err := decodeRaw(request.MobilePublicKey, ed25519.PublicKeySize, "mobile_public_key")
	if err != nil {
		return nil, err
	}
	requestNonce, err := decodeRaw(request.Nonce, 32, "nonce")
	if err != nil {
		return nil, err
	}
	desktopNonce, err := decodeRaw(response.DesktopNonce, 32, "desktop_nonce")
	if err != nil {
		return nil, err
	}
	var buf bytes.Buffer
	writeString(&buf, contextResponse)
	buf.WriteByte(protocolVersion)
	writeString(&buf, responseType)
	writeString(&buf, response.RequestID)
	writeString(&buf, response.Decision)
	writeString(&buf, response.DesktopID)
	writeBytes(&buf, desktopPublicKey)
	writeString(&buf, request.MobileID)
	writeBytes(&buf, mobileKey)
	writeBytes(&buf, requestNonce)
	writeBytes(&buf, desktopNonce)
	_ = binary.Write(&buf, binary.BigEndian, response.IssuedAt)
	return buf.Bytes(), nil
}
