// Code generated by smithy-swift-codegen. DO NOT EDIT!



extension GetCityAnnouncementsInput {

    static func urlPathProvider(_ value: GetCityAnnouncementsInput) -> Swift.String? {
        guard let cityId = value.cityId else {
            return nil
        }
        return "/cities/\(cityId.urlPercentEncoding())/announcements"
    }
}
