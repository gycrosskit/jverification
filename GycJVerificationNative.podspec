Pod::Spec.new do |s|
  s.name = 'GycJVerificationNative'
  s.version = '0.1.4'
  s.summary = '极光一键认证 SDK 的同意、预取号、授权与释放边界'
  s.homepage = 'https://github.com/gycrosskit/jverification'
  s.license = { :type => 'Apache-2.0', :file => 'LICENSE' }
  s.author = 'GY CrossKit'
  s.source = { :git => 'https://github.com/gycrosskit/jverification.git', :tag => s.version.to_s }
  s.ios.deployment_target = '15.0'
  s.swift_version = '5.9'
  s.source_files = 'iosApp/Sources/GycJVerificationNative/*.{swift,h}'
  s.public_header_files = 'iosApp/Sources/GycJVerificationNative/*.h'
  s.dependency 'JVerification', '3.4.7'
  s.dependency 'JCore', '5.5.1'
end
