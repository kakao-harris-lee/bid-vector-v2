// caddy v2.11.7 의 `cmd/caddy/main.go` 와 같은 본문이다 — 표준 모듈만 싣고 플러그인을 더하지
// 않는다(공식 이미지 바이너리와 같은 기능 집합). 바꾸는 것은 go.mod 의 의존 판뿐이다.
package main

import (
	caddycmd "github.com/caddyserver/caddy/v2/cmd"

	// plug in Caddy modules here
	_ "github.com/caddyserver/caddy/v2/modules/standard"
)

func main() {
	caddycmd.Main()
}
